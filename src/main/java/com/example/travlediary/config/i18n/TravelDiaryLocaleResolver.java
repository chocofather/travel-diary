package com.example.travlediary.config.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

public class TravelDiaryLocaleResolver extends CookieLocaleResolver {

    public static final String COOKIE_NAME = "TRAVEL_DIARY_LOCALE";

    public TravelDiaryLocaleResolver() {
        super(COOKIE_NAME);
        // 기본 언어를 고정하지 않는다. 저장된 선택(쿠키)이 없으면 브라우저 Accept-Language 로 정한다.
        setDefaultLocaleFunction(TravelDiaryLocaleResolver::detectBrowserLanguage);
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

    /**
     * 사용자가 직접 고른 언어(쿠키)가 1순위다.
     * 해석할 수 없는 값이면 null 을 돌려 저장된 선택이 없는 것으로 보고 브라우저 감지로 넘긴다.
     */
    @Override
    protected Locale parseLocaleValue(String localeValue) {
        return SupportedLanguage.normalize(localeValue)
                .map(SupportedLanguage::getLocale)
                .orElse(null);
    }

    /**
     * 저장된 선택이 없는 첫 방문에서만 브라우저 선호 언어를 본다.
     * 지원하지 않는 언어를 선호하면 English 로 떨어진다.
     * Accept-Language 자체를 보내지 않는 요청(봇/도구)은 선호가 없는 것이므로
     * 사이트 기본 언어인 한국어를 그대로 쓴다.
     */
    private static Locale detectBrowserLanguage(HttpServletRequest request) {
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return SupportedLanguage.KOREAN.getLocale();
        }
        return SupportedLanguage.fromAcceptLanguage(acceptLanguage).getLocale();
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
