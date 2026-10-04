package com.tripbora.config.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

/**
 * Travel Diary 시절 언어 선택 쿠키(TRAVEL_DIARY_LOCALE)를 TripBora 쿠키로 옮긴다.
 *
 * <ul>
 *   <li>새 쿠키 없음 + 이전 쿠키 유효: 이번 요청에 이전 값을 적용하고 새 쿠키를 발급한 뒤 이전 쿠키를 만료한다.</li>
 *   <li>새 쿠키와 이전 쿠키가 함께 있음: 새 쿠키를 그대로 쓰고 이전 쿠키만 만료한다.</li>
 *   <li>이전 쿠키 값이 지원 언어가 아님: 이전 쿠키만 만료하고 기존 기본 언어 판단에 맡긴다.</li>
 * </ul>
 *
 * <p>새 쿠키는 {@link LocaleResolver#setLocale} 로 발급한다. 그래야 Path/Max-Age/HttpOnly/SameSite/Secure 가
 * POST /locale 이 쓰는 쿠키와 같고, 같은 요청 안의 언어 판단에도 곧바로 반영된다.
 * 관리자 경로의 한국어 고정은 {@link TripBoraLocaleResolver} 가 그대로 맡는다.
 *
 * <p>호환 기간(운영 배포 후 365일)이 지나면 이 클래스와 테스트를 함께 지운다.
 * 이전 쿠키 이름은 이 클래스에만 둔다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LegacyLocaleCookieMigrationFilter extends OncePerRequestFilter {

    public static final String LEGACY_COOKIE_NAME = "TRAVEL_DIARY_LOCALE";

    /** 공개 캐시가 붙는 배포 정적 리소스. 이런 응답에는 Set-Cookie 를 싣지 않는다. */
    private static final List<String> STATIC_PATH_PREFIXES =
            List.of("/css/", "/js/", "/images/", "/fonts/", "/webjars/", "/uploads/");

    private final LocaleResolver localeResolver;

    public LegacyLocaleCookieMigrationFilter(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return STATIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Cookie legacy = WebUtils.getCookie(request, LEGACY_COOKIE_NAME);
        if (legacy != null) {
            if (WebUtils.getCookie(request, TripBoraLocaleResolver.COOKIE_NAME) == null) {
                SupportedLanguage.normalize(legacy.getValue())
                        .ifPresent(language ->
                                localeResolver.setLocale(request, response, language.getLocale()));
            }
            response.addHeader(HttpHeaders.SET_COOKIE, expiredLegacyCookie().toString());
        }
        filterChain.doFilter(request, response);
    }

    /** 이전 쿠키를 지운다. 발급 때와 같은 Path 여야 브라우저가 같은 쿠키로 보고 지운다. */
    private static ResponseCookie expiredLegacyCookie() {
        return ResponseCookie.from(LEGACY_COOKIE_NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }
}
