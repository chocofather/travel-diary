package com.example.travlediary.controller.search;

import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.service.search.GlobalSearchService;
import com.example.travlediary.service.search.GlobalSearchType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String query,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "1") String page,
                         Model model) {
        GlobalSearchPage searchPage = globalSearchService.search(query, type, parsePage(page));
        model.addAttribute("searchPage", searchPage);
        model.addAttribute("searchTypes", GlobalSearchType.values());
        model.addAttribute("pageTitle", "통합검색 | 여행일기");
        return "search";
    }

    private int parsePage(String page) {
        try {
            return Math.max(Integer.parseInt(page), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
