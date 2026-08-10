package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final String FRAGMENT_VIEW = "travel-info/fragments/list-results :: results";
    private static final Set<String> ALLOWED_RETURN_QUERY_PARAMETERS = Set.of(
            "scope", "contentType", "categoryId", "page", "size");

    private final TravelInfoService travelInfoService;
    private final InfoCategoryService infoCategoryService;

    @GetMapping("/travel-info")
    public String list(@RequestParam(required = false) String scope,
                       @RequestParam(required = false) String contentType,
                       @RequestParam(name = "categoryId", required = false) List<String> categoryIdValues,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "12") int size,
                       @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                       Model model) {
        TravelInfoScope safeScope = parseEnum(scope, TravelInfoScope.class);
        TravelInfoContentType safeContentType = parseEnum(contentType, TravelInfoContentType.class);
        List<Long> safeCategoryIds = parsePositiveLongs(categoryIdValues);
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        long offset = (long) (safePage - 1) * safeSize;

        List<TravelInfoListItemDto> travelInfoList = travelInfoService.getPublicList(
                safeScope, safeContentType, safeCategoryIds, offset, safeSize);
        long totalCount = travelInfoService.countPublicList(
                safeScope, safeContentType, safeCategoryIds);
        int totalPages = totalCount == 0
                ? 0
                : (int) Math.ceil((double) totalCount / safeSize);
        int pageStart = Math.max(1, safePage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("travelInfoList", travelInfoList);
        model.addAttribute("scope", safeScope);
        model.addAttribute("contentType", safeContentType);
        model.addAttribute("categoryIds", safeCategoryIds);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("listUrl", buildListUrl(
                safeScope, safeContentType, safeCategoryIds, safePage, safeSize));

        if ("XMLHttpRequest".equals(requestedWith)) {
            return FRAGMENT_VIEW;
        }

        model.addAttribute("categories", infoCategoryService.getVisible());
        model.addAttribute("pageTitle", "여행정보");
        return "travel-info/list";
    }

    @GetMapping("/travel-info/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String returnUrl,
                         Model model) {
        TravelInfoDetailDto travelInfo = travelInfoService.getPublicDetail(id);
        model.addAttribute("travelInfo", travelInfo);
        model.addAttribute("listUrl", validateReturnUrl(returnUrl));
        model.addAttribute("pageTitle", travelInfo.getTitle() + " | 여행정보");
        return "travel-info/detail";
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
            List<Long> safeCategoryIds = parseReturnCategoryIds(query.get("categoryId"));
            int safePage = parseReturnPositiveInt(query, "page", 1, Integer.MAX_VALUE);
            int safeSize = parseReturnPositiveInt(
                    query, "size", DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
            return buildListUrl(
                    safeScope, safeContentType, safeCategoryIds, safePage, safeSize);
        } catch (IllegalArgumentException ignored) {
            return LIST_PATH;
        }
    }

    private String buildListUrl(TravelInfoScope scope,
                                TravelInfoContentType contentType,
                                List<Long> categoryIds,
                                int page,
                                int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(LIST_PATH);
        if (scope != null) {
            builder.queryParam("scope", scope.name());
        }
        if (contentType != null) {
            builder.queryParam("contentType", contentType.name());
        }
        if (categoryIds != null) {
            categoryIds.forEach(categoryId -> builder.queryParam("categoryId", categoryId));
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        if (size != DEFAULT_PAGE_SIZE) {
            builder.queryParam("size", size);
        }
        return builder.build().encode().toUriString();
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
