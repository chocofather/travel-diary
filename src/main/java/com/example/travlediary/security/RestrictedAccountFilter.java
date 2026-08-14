package com.example.travlediary.security;

import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.UserSanctionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 이용제한(RESTRICTED) 회원의 접근을 제한 안내 화면으로 모은다.
 * 세션에 담긴 상태를 믿지 않고 요청마다 users.status 를 다시 확인하므로
 * 로그인 중인 회원이 정지되어도 다음 요청부터 바로 반영된다.
 */
public class RestrictedAccountFilter extends OncePerRequestFilter {

    public static final String RESTRICTED_PATH = "/account/restricted";

    /** 제한 회원도 반드시 접근할 수 있어야 하는 경로. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            RESTRICTED_PATH,
            "/logout",
            "/login",
            "/appeals",      // 추후 이의제기 화면 연결 지점
            "/css/",
            "/js/",
            "/images/",
            "/fonts/",
            "/uploads/",
            "/favicon.ico",
            "/error"
    );

    private final UserMapper userMapper;
    private final UserSanctionService userSanctionService;

    public RestrictedAccountFilter(UserMapper userMapper,
                                   UserSanctionService userSanctionService) {
        this.userMapper = userMapper;
        this.userSanctionService = userSanctionService;
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
        if (userId == null || !isRestricted(userId)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 기간이 끝난 제재는 이 시점에 해제하고 요청을 그대로 진행한다.
        if (userSanctionService.releaseIfExpired(userId)) {
            filterChain.doFilter(request, response);
            return;
        }
        blockRequest(request, response);
    }

    /** 관리자와 비로그인 요청은 검사 대상이 아니다. */
    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return admin ? null : details.getId();
    }

    private boolean isRestricted(Long userId) {
        return userMapper.findStatusById(userId) == UserStatus.RESTRICTED;
    }

    private void blockRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Restricted\"}");
            return;
        }
        response.sendRedirect(RESTRICTED_PATH);
    }
}
