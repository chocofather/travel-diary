package com.tripbora.controller;

import com.tripbora.config.InternalRedirectValidator;
import com.tripbora.config.i18n.SupportedLanguage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

@Controller
public class LocaleController {

    private final LocaleResolver localeResolver;

    public LocaleController(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @PostMapping("/locale")
    public String changeLocale(
            @RequestParam(name = "languageTag", required = false) String languageTag,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            HttpServletRequest request,
            HttpServletResponse response) {
        SupportedLanguage language = SupportedLanguage.fromLanguageTag(languageTag)
                .orElse(SupportedLanguage.KOREAN);
        localeResolver.setLocale(request, response, language.getLocale());

        String safeReturnTo = InternalRedirectValidator.normalize(returnTo);
        return "redirect:" + (safeReturnTo == null ? "/" : safeReturnTo);
    }
}
