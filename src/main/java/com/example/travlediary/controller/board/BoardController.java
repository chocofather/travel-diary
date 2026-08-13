package com.example.travlediary.controller.board;

import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.board.BoardService;
import com.example.travlediary.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final CountryCategoryService countryCategoryService;

    // 1. 전체 페이지(SSR 최초 진입)
    @GetMapping("/board/list")
    public String boardListPage(
            @RequestParam(value = "boardType", required = false) String boardType,
            @RequestParam(value = "postType", required = false) String postType,
            @RequestParam(value = "scope", defaultValue = "all") String scope,
            @RequestParam(value = "countryId", required = false) Long countryId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        CourseBoardFilter courseFilter = resolveCourseBoardFilter(boardType, scope, countryId);
        List<BoardListDto> boardList = boardService.getBoardList(boardType, postType,
                courseFilter.scope(), courseFilter.countryId(), sort, safePage, safeSize);
        int totalCount = boardService.getBoardCount(boardType, postType,
                courseFilter.scope(), courseFilter.countryId());
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);

        model.addAttribute("boardList", boardList);
        model.addAttribute("boardType", boardType);
        model.addAttribute("postType", postType);
        addCourseFilterModel(model, courseFilter);
        model.addAttribute("sort", sort);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("pageTitle", "여행 커뮤니티");

        return "board/list";
    }

    // 2. fragment만 반환 (AJAX 페이징/정렬/검색 등)
    @GetMapping("/board/fragment")
    public String boardFragment(
            @RequestParam(value = "boardType", required = false) String boardType,
            @RequestParam(value = "postType", required = false) String postType,
            @RequestParam(value = "scope", defaultValue = "all") String scope,
            @RequestParam(value = "countryId", required = false) Long countryId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        CourseBoardFilter courseFilter = resolveCourseBoardFilter(boardType, scope, countryId);
        List<BoardListDto> boardList = boardService.getBoardList(boardType, postType,
                courseFilter.scope(), courseFilter.countryId(), sort, safePage, safeSize);
        int totalCount = boardService.getBoardCount(boardType, postType,
                courseFilter.scope(), courseFilter.countryId());
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);

        model.addAttribute("boardList", boardList);
        model.addAttribute("boardType", boardType);
        model.addAttribute("postType", postType);
        addCourseFilterModel(model, courseFilter);
        model.addAttribute("sort", sort);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", safeSize);

        return "board/fragment :: boardListFragment";
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private CourseBoardFilter resolveCourseBoardFilter(String boardType, String scope, Long countryId) {
        boolean courseBoard = "course".equalsIgnoreCase(boardType);
        if (!courseBoard) {
            return new CourseBoardFilter(false, "all", null, null, List.of());
        }

        String normalizedScope = switch (scope == null ? "all" : scope.toLowerCase(Locale.ROOT)) {
            case "domestic" -> "domestic";
            case "overseas" -> "overseas";
            default -> "all";
        };
        List<CountryCategory> overseasCountries = countryCategoryService.getCourseCountries().stream()
                .filter(country -> country.getParentId() != null)
                .toList();
        Long selectedCountryId = "overseas".equals(normalizedScope)
                && countryId != null
                && overseasCountries.stream().anyMatch(country -> country.getId().equals(countryId))
                ? countryId : null;
        String selectedCountryName = overseasCountries.stream()
                .filter(country -> country.getId().equals(selectedCountryId))
                .map(CountryCategory::getRegionName)
                .findFirst()
                .orElse(null);
        return new CourseBoardFilter(true, normalizedScope, selectedCountryId,
                selectedCountryName, overseasCountries);
    }

    private void addCourseFilterModel(Model model, CourseBoardFilter filter) {
        model.addAttribute("courseBoard", filter.courseBoard());
        model.addAttribute("scope", filter.scope());
        model.addAttribute("countryId", filter.countryId());
        model.addAttribute("countryName", filter.countryName());
        model.addAttribute("overseasCourseCountries", filter.overseasCountries());
        model.addAttribute("emptyMessage", filter.courseBoard()
                ? "해당 조건의 여행 코스가 없습니다."
                : "등록된 게시글이 없습니다.");
    }

    private record CourseBoardFilter(boolean courseBoard, String scope, Long countryId, String countryName,
                                     List<CountryCategory> overseasCountries) {
    }
}
