package com.example.travlediary.security;

import com.example.travlediary.service.user.MissingEmailRegistrationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 이메일 없이 남아 있는 예전 Kakao/Naver 회원을 이메일 등록 화면으로 모은다.
 *
 * <p>로그인 성공 시의 이동만 바꾸면 URL 을 직접 치거나 예전 북마크로 들어올 수 있으므로
 * 요청마다 DB 로 대상 여부를 다시 확인한다. 이메일 등록과 인증이 끝나면 다음 요청부터 바로 풀린다.
 *
 * <p>판정 조건이 status = ACTIVE 라서 이용제한·탈퇴 유예 회원은 애초에 걸리지 않는다.
 * 그 상태의 격리는 각자의 필터가 그대로 맡고, 이 필터가 그 정책을 앞지르지 않는다.
 */
public class MissingEmailAccountFilter extends OncePerRequestFilter {

    public static final String EMAIL_REQUIRED_PATH = "/account/email-required";

    /** 이메일 등록과 인증을 끝내는 데 반드시 필요한 최소 경로. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            EMAIL_REQUIRED_PATH,
            "/logout",
            "/login",
            "/locale",                          // 안내 화면의 언어 전환
            "/users/verify",                    // 메일의 인증 링크
            "/users/register/verify-waiting",   // 인증 대기 화면
            "/users/verification/",             // 진행 상태 확인 / 재발송
            "/css/",
            "/js/",
            "/images/",
            "/fonts/",
            "/uploads/",
            "/favicon.ico",
            "/error"
    );

    private final MissingEmailRegistrationService missingEmailRegistrationService;

    public MissingEmailAccountFilter(
            MissingEmailRegistrationService missingEmailRegistrationService) {
        this.missingEmailRegistrationService = missingEmailRegistrationService;
    }

    /** 허용 경로는 아예 검사하지 않아 리다이렉트 루프가 생기지 않는다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ALLOWED_PREFIXES.stream().anyMatch(
                allowed -> path.equals(allowed) || path.startsWith(allowed + "/")
                        || (allowed.endsWith("/") && path.startsWith(allowed)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Long userId = authenticatedUserId();
        if (userId == null
                || !missingEmailRegistrationService.requiresEmailRegistration(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"EmailVerificationRequired\"}");
            return;
        }
        response.sendRedirect(EMAIL_REQUIRED_PATH);
    }

    /** 비로그인 요청은 검사 대상이 아니다. */
    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return details.getId();
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }
}
