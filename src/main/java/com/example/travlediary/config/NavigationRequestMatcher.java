package com.example.travlediary.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.Set;

/**
 * 로그인 후 복귀 대상(SavedRequest)으로 저장할 만한 "실제 페이지 이동" 요청만 통과시킨다.
 *
 * <p>기본 {@code HttpSessionRequestCache} 는 인증이 필요한 모든 요청을 저장하기 때문에,
 * 브라우저가 배경에서 보내는 보조 요청(예: DevTools 의
 * {@code /.well-known/appspecific/com.chrome.devtools.json}, favicon, 정적 리소스, fetch/XHR)이
 * 원래 보던 페이지의 SavedRequest 를 덮어써 로그인 후 엉뚱한 URL 로 이동하게 된다.
 */
public class NavigationRequestMatcher implements RequestMatcher {

    /** 브라우저 보조 요청 경로 */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/.well-known/",
            "/css/",
            "/js/",
            "/images/",
            "/uploads/",
            "/webjars/",
            "/resources/",
            "/static/");

    private static final Set<String> EXCLUDED_PATHS = Set.of("/favicon.ico", "/error");

    /** 정적 리소스 확장자 (com.chrome.devtools.json 처럼 .json 도 포함) */
    private static final List<String> EXCLUDED_SUFFIXES = List.of(
            ".css", ".js", ".map", ".json",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp",
            ".woff", ".woff2", ".ttf", ".eot");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = pathWithinApplication(request);
        if (EXCLUDED_PATHS.contains(path)) {
            return false;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        String lowerCasePath = path.toLowerCase();
        for (String suffix : EXCLUDED_SUFFIXES) {
            if (lowerCasePath.endsWith(suffix)) {
                return false;
            }
        }

        // fetch/XHR 은 페이지 이동이 아니다
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return false;
        }
        // 최신 브라우저는 페이지 이동에만 Sec-Fetch-Dest: document 를 보낸다
        String fetchDestination = request.getHeader("Sec-Fetch-Dest");
        if (fetchDestination != null && !"document".equalsIgnoreCase(fetchDestination)) {
            return false;
        }
        // 문서를 원하지 않는 요청(JSON 전용 등)도 제외
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html") || accept.contains("*/*");
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
