package com.example.travlediary.config;

import com.example.travlediary.config.i18n.SupportedLanguage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Locale;

@ControllerAdvice
public class GlobalRequestControllerAdvice {

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
    }

    @ModelAttribute
    public void localeAttributes(Model model, Locale locale) {
        SupportedLanguage currentLanguage = SupportedLanguage.fromLocale(locale)
                .orElse(SupportedLanguage.KOREAN);
        model.addAttribute("supportedLanguages", SupportedLanguage.all());
        model.addAttribute("currentLanguage", currentLanguage);
        model.addAttribute("currentLanguageTag", currentLanguage.getLanguageTag());
    }

}
