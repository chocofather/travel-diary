package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class TravelInfoController {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final String FRAGMENT_VIEW = "travel-info/fragments/list-results :: results";

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

        if ("XMLHttpRequest".equals(requestedWith)) {
            return FRAGMENT_VIEW;
        }

        model.addAttribute("categories", infoCategoryService.getVisible());
        model.addAttribute("pageTitle", "여행정보");
        return "travel-info/list";
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
