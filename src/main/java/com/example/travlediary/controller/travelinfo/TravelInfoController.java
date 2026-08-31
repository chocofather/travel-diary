package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoSearchKeyword;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class TravelInfoController {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final String LIST_PATH = "/travel-info";
    private static final String FRAGMENT_VIEW = "travel-info/fragments/list-async :: response";
    private static final String SORT_LATEST = "latest";
    private static final String SORT_VIEWS = "views";
    private static final Set<String> ALLOWED_RETURN_QUERY_PARAMETERS = Set.of(
            "keyword", "scope", "contentType", "categoryId", "sort", "page", "size");

    private final TravelInfoService travelInfoService;
    private final FestivalDetailService festivalDetailService;
    private final InfoCategoryService infoCategoryService;

    @GetMapping("/travel-info")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) String contentType,
                       @RequestParam(name = "categoryId", required = false) List<String> categoryIdValues,
                       @RequestParam(required = false) String sort,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "12") int size,
                       @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model) {
        String safeKeyword = TravelInfoSearchKeyword.normalize(keyword);
        TravelInfoScope safeScope = parseEnum(scope, TravelInfoScope.class);
        TravelInfoContentType safeContentType = parseEnum(contentType, TravelInfoContentType.class);
        List<Long> safeCategoryIds = parsePositiveLongs(categoryIdValues);
        String safeSort = normalizeSort(sort);
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        long offset = (long) (safePage - 1) * safeSize;

        List<TravelInfoListItemDto> travelInfoList = travelInfoService.getPublicList(
                safeScope, safeContentType, safeCategoryIds, safeKeyword,
                safeSort, offset, safeSize);
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        travelInfoService.populatePublicListBookmarks(travelInfoList, currentUserId);
        long totalCount = travelInfoService.countPublicList(
                safeScope, safeContentType, safeCategoryIds, safeKeyword);
        int totalPages = totalCount == 0
                ? 0
                : (int) Math.ceil((double) totalCount / safeSize);
        int pageStart = Math.max(1, safePage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("travelInfoList", travelInfoList);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("scope", safeScope);
        model.addAttribute("contentType", safeContentType);
        model.addAttribute("categoryIds", safeCategoryIds);
        model.addAttribute("sort", safeSort);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("listUrl", buildListUrl(
                safeKeyword, safeScope, safeContentType,
                safeCategoryIds, safeSort, safePage, safeSize));
        model.addAttribute("categories", infoCategoryService.getVisibleByContentType(
                safeContentType == null ? TravelInfoContentType.GENERAL : safeContentType));

        if ("XMLHttpRequest".equals(requestedWith)) {
            return FRAGMENT_VIEW;
        }

        model.addAttribute("pageTitle", "여행정보");
        return "travel-info/list";
    }

    @GetMapping("/travel-info/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String returnUrl,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        if (festivalDetailService.isPublicFestival(id)) {
            return festivalRedirect(id, returnUrl);
        }
        TravelInfoDetailDto travelInfo = travelInfoService.getPublicDetail(id);
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        travelInfoService.populatePublicDetailBookmark(travelInfo, currentUserId);
        model.addAttribute("travelInfo", travelInfo);
        model.addAttribute("listUrl", validateReturnUrl(returnUrl));
        model.addAttribute("pageTitle", travelInfo.getTitle() + " | 여행정보");
        return "travel-info/detail";
    }

    @GetMapping("/festivals/{id:\\d+}")
    public String festivalDetail(@PathVariable Long id,
                                 @RequestParam(required = false) String returnUrl,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        FestivalDetailDto festival = festivalDetailService.getPublicDetail(id);
        Long currentUserId = userDetails == null ? null : userDetails.getId();
        travelInfoService.populatePublicDetailBookmark(festival.getTravelInfo(), currentUserId);
        model.addAttribute("festival", festival);
        model.addAttribute("listUrl", validateReturnUrl(returnUrl));
        model.addAttribute("pageTitle", festival.getTravelInfo().getTitle() + " | 축제·행사");
        return "festivals/detail";
    }

    private String festivalRedirect(Long id, String returnUrl) {
        String festivalPath = "/festivals/" + id;
        if (returnUrl == null || returnUrl.isBlank()) {
            return "redirect:" + festivalPath;
        }
        String safeReturnUrl = URLEncoder.encode(
                validateReturnUrl(returnUrl), StandardCharsets.UTF_8);
        return "redirect:" + festivalPath + "?returnUrl=" + safeReturnUrl;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<Long> parsePositiveLongs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                long parsed = Long.parseLong(value.strip());
                if (parsed > 0) {
                    uniqueIds.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // Invalid query values are ignored instead of failing the public list request.
            }
        }
        return List.copyOf(uniqueIds);
    }

    private String validateReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()
                || returnUrl.indexOf('\\') >= 0
                || returnUrl.chars().anyMatch(Character::isISOControl)) {
            return LIST_PATH;
        }

        try {
            UriComponents uri = UriComponentsBuilder.fromUriString(returnUrl.strip()).build();
            if (uri.getScheme() != null
                    || uri.getHost() != null
                    || uri.getFragment() != null
                    || !LIST_PATH.equals(uri.getPath())) {
                return LIST_PATH;
            }

            MultiValueMap<String, String> query = uri.getQueryParams();
            if (!ALLOWED_RETURN_QUERY_PARAMETERS.containsAll(query.keySet())) {
                return LIST_PATH;
            }

            TravelInfoScope safeScope = parseReturnEnum(query, "scope", TravelInfoScope.class);
            TravelInfoContentType safeContentType = parseReturnEnum(
                    query, "contentType", TravelInfoContentType.class);
            String safeKeyword = parseReturnKeyword(query);
            List<Long> safeCategoryIds = parseReturnCategoryIds(query.get("categoryId"));
            String safeSort = parseReturnSort(query);
            int safePage = parseReturnPositiveInt(query, "page", 1, Integer.MAX_VALUE);
            int safeSize = parseReturnPositiveInt(
                    query, "size", DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
            return buildListUrl(
                    safeKeyword, safeScope, safeContentType,
                    safeCategoryIds, safeSort, safePage, safeSize);
        } catch (IllegalArgumentException ignored) {
            return LIST_PATH;
        }
    }

    private String buildListUrl(String keyword,
                                TravelInfoScope scope,
                                TravelInfoContentType contentType,
                                List<Long> categoryIds,
                                String sort,
                                int page,
                                int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(LIST_PATH);
        if (keyword != null) {
            builder.queryParam("keyword", keyword);
        }
        if (scope != null) {
            builder.queryParam("scope", scope.name());
        }
        if (contentType != null) {
            builder.queryParam("contentType", contentType.name());
        }
        if (categoryIds != null) {
            categoryIds.forEach(categoryId -> builder.queryParam("categoryId", categoryId));
        }
        if (SORT_VIEWS.equals(sort)) {
            builder.queryParam("sort", SORT_VIEWS);
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        if (size != DEFAULT_PAGE_SIZE) {
            builder.queryParam("size", size);
        }
        return builder.build().encode().toUriString();
    }

    private String parseReturnSort(MultiValueMap<String, String> query) {
        String value = singleReturnValue(query, "sort");
        if (value == null || value.isBlank() || SORT_LATEST.equals(value)) {
            return SORT_LATEST;
        }
        if (SORT_VIEWS.equals(value)) {
            return SORT_VIEWS;
        }
        throw new IllegalArgumentException("Invalid return URL sort");
    }

    private String normalizeSort(String sort) {
        if (sort == null) {
            return SORT_LATEST;
        }
        return SORT_VIEWS.equals(sort.strip()) ? SORT_VIEWS : SORT_LATEST;
    }

    private String parseReturnKeyword(MultiValueMap<String, String> query) {
        String value = singleReturnValue(query, "keyword");
        if (value == null || value.isBlank()) {
            return null;
        }
        String decoded = UriUtils.decode(value, StandardCharsets.UTF_8);
        if (decoded.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid return URL keyword");
        }
        return TravelInfoSearchKeyword.normalize(decoded);
    }

    private <E extends Enum<E>> E parseReturnEnum(MultiValueMap<String, String> query,
                                                   String name,
                                                   Class<E> enumType) {
        String value = singleReturnValue(query, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        E parsed = parseEnum(value, enumType);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid return URL enum");
        }
        return parsed;
    }

    private List<Long> parseReturnCategoryIds(List<String> values) {
        if (values == null) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Invalid return URL category");
            }
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Invalid return URL category");
            }
            uniqueIds.add(parsed);
        }
        return List.copyOf(uniqueIds);
    }

    private int parseReturnPositiveInt(MultiValueMap<String, String> query,
                                       String name,
                                       int defaultValue,
                                       int maximum) {
        String value = singleReturnValue(query, name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        long parsed = Long.parseLong(value);
        if (parsed < 1 || parsed > maximum) {
            throw new IllegalArgumentException("Invalid return URL number");
        }
        return (int) parsed;
    }

    private String singleReturnValue(MultiValueMap<String, String> query, String name) {
        List<String> values = query.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("Duplicate return URL parameter");
        }
        return values.get(0);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
