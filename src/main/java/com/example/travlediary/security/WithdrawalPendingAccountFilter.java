package com.example.travlediary.security;

import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
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
 * 탈퇴 유예(WITHDRAWAL_PENDING) 회원의 접근을 탈퇴 안내 화면으로 모은다.
 *
 * <p>이 상태의 인증은 "안내 화면과 복구 절차까지만" 허용한다는 뜻이지 서비스 이용 권한이 아니다.
 * 세션에 담긴 상태를 믿지 않고 요청마다 users.status 를 다시 확인하므로
 * 복구가 끝나면 다음 요청부터 바로 격리가 풀리고, 로그인 중에 탈퇴가 접수되면 바로 격리된다.
 *
 * <p>이용제한(RESTRICTED) 격리와는 허용 경로도 이동 화면도 다르므로 필터를 따로 둔다.
 */
public class WithdrawalPendingAccountFilter extends OncePerRequestFilter {

    public static final String WITHDRAWAL_PENDING_PATH = "/account/withdrawal-pending";

    /** 탈퇴 유예 회원도 반드시 접근할 수 있어야 하는 최소 경로. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            WITHDRAWAL_PENDING_PATH,
            "/logout",
            "/login",
            "/locale",                          // 안내 화면의 언어 전환
            "/users/recover-account/confirm",   // 메일 복구 링크 확인/확정
            "/css/",
            "/js/",
            "/images/",
            "/fonts/",
            "/uploads/",
            "/favicon.ico",
            "/error"
    );

    private final UserMapper userMapper;

    public WithdrawalPendingAccountFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
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
        if (userId == null || !isWithdrawalPending(userId)) {
            filterChain.doFilter(request, response);
            return;
        }
        blockRequest(request, response);
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

    private boolean isWithdrawalPending(Long userId) {
        return userMapper.findStatusById(userId) == UserStatus.WITHDRAWAL_PENDING;
    }

    private void blockRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"WithdrawalPending\"}");
            return;
        }
        response.sendRedirect(WITHDRAWAL_PENDING_PATH);
    }
}
