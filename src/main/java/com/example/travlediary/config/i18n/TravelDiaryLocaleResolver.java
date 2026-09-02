package com.example.travlediary.config.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

public class TravelDiaryLocaleResolver extends CookieLocaleResolver {

    public static final String COOKIE_NAME = "TRAVEL_DIARY_LOCALE";

    public TravelDiaryLocaleResolver() {
        super(COOKIE_NAME);
        setDefaultLocale(SupportedLanguage.KOREAN.getLocale());
        setCookiePath("/");
        setCookieMaxAge(Duration.ofDays(365));
        setCookieHttpOnly(true);
        setCookieSameSite("Lax");
        setLanguageTagCompliant(true);
        setRejectInvalidCookies(false);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return SupportedLanguage.KOREAN.getLocale();
        }
        return super.resolveLocale(request);
    }

    @Override
    public LocaleContext resolveLocaleContext(HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return new SimpleLocaleContext(SupportedLanguage.KOREAN.getLocale());
        }
        return super.resolveLocaleContext(request);
    }

    @Override
    protected Locale parseLocaleValue(String localeValue) {
        return SupportedLanguage.fromLanguageTag(localeValue)
                .orElse(SupportedLanguage.KOREAN)
                .getLocale();
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        return "/admin".equals(path) || path.startsWith("/admin/");
    }
}
