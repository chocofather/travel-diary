package com.example.travlediary.controller.search;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.service.search.GlobalSearchService;
import com.example.travlediary.service.search.GlobalSearchType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final MessageSource messageSource;

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String query,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "1") String page,
                         Model model) {
        SupportedLanguage requestedLanguage = SupportedLanguage
                .fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
        GlobalSearchPage searchPage = globalSearchService.search(
                query, type, parsePage(page), requestedLanguage);
        model.addAttribute("searchPage", searchPage);
        model.addAttribute("searchTypes", GlobalSearchType.values());
        model.addAttribute("pageTitle", messageSource.getMessage(
                "search.pageTitle", null, requestedLanguage.getLocale()));
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
