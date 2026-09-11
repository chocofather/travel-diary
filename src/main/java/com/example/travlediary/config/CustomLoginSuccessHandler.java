package com.example.travlediary.config;

import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.security.ExpiredWithdrawalResponse;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.RestrictedAccountFilter;
import com.example.travlediary.security.WithdrawalPendingAccountFilter;
import com.example.travlediary.service.user.WithdrawalGraceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Component
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserMapper userMapper;
    private final LoginThrottle loginThrottle;
    private final WithdrawalGraceService withdrawalGraceService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Autowired
    public CustomLoginSuccessHandler(UserMapper userMapper,
                                     LoginThrottle loginThrottle,
                                     WithdrawalGraceService withdrawalGraceService) {
        this.userMapper = userMapper;
        this.loginThrottle = loginThrottle;
        this.withdrawalGraceService = withdrawalGraceService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getId();
        loginThrottle.recordSuccess(userDetails.getUsername());
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute(LoginFormState.SESSION_ATTRIBUTE);
        }

        // 1) 인증 완료 후에는 username 이 아니라 DB 회원 ID를 세션 식별값으로 사용한다.
        request.getSession().setAttribute("userId", userId);

        // 2) 상태 격리 화면은 저장된 요청보다 우선한다. 인증은 됐지만 서비스 이용 권한은 아니다.
        UserStatus status = userMapper.findStatusById(userId);
        if (status == UserStatus.RESTRICTED) {
            requestCache.removeRequest(request, response);
            response.sendRedirect(RestrictedAccountFilter.RESTRICTED_PATH);
            return;
        }
        if (status == UserStatus.WITHDRAWAL_PENDING) {
            requestCache.removeRequest(request, response);
            // 유예 여부는 배치 실행 여부가 아니라 purge_scheduled_at 으로 판정한다.
            // 이미 끝난 계정이면 본인 확인이 끝난 지금 최종 파기하고 새 가입으로 보낸다.
            if (withdrawalGraceService.resolveAccess(userId, null)
                    == WithdrawalGraceService.Outcome.GRACE_ENDED) {
                ExpiredWithdrawalResponse.endSession(request, response);
                response.sendRedirect(ExpiredWithdrawalResponse.REGISTER_PATH);
                return;
            }
            response.sendRedirect(WithdrawalPendingAccountFilter.WITHDRAWAL_PENDING_PATH);
            return;
        }

        // 3) 같은 로그인 세션에서 저장된 원래 요청을 우선하되 권한을 다시 확인한다.
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        String savedRedirect = savedRequestRedirect(request, response);
        String requestedRedirect = InternalRedirectValidator.normalize(
                request.getParameter("redirect"));
        String target = savedRedirect != null ? savedRedirect : requestedRedirect;

        requestCache.removeRequest(request, response);
        if (target == null || (isAdminPath(target) && !admin)) {
            response.sendRedirect("/");
        } else {
            response.sendRedirect(target);
        }
    }

    private boolean isAdminPath(String redirect) {
        String path = URI.create(redirect).getPath();
        return "/admin".equals(path) || path.startsWith("/admin/");
    }

    private String savedRequestRedirect(HttpServletRequest request,
                                        HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null || !"GET".equalsIgnoreCase(savedRequest.getMethod())) {
            return null;
        }

        try {
            URI savedUri = new URI(savedRequest.getRedirectUrl());
            if (!sameOrigin(request, savedUri)) {
                return null;
            }
            StringBuilder target = new StringBuilder(savedUri.getRawPath());
            String query = originalQuery(savedUri.getRawQuery());
            if (query != null) {
                target.append('?').append(query);
            }
            return InternalRedirectValidator.normalize(target.toString());
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String originalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (String parameter : rawQuery.split("&")) {
            if (parameter.isBlank() || "continue".equals(parameter)) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(parameter);
        }
        return result.isEmpty() ? null : result.toString();
    }

    private boolean sameOrigin(HttpServletRequest request, URI uri) {
        return request.getScheme().equalsIgnoreCase(uri.getScheme())
                && request.getServerName().equalsIgnoreCase(uri.getHost())
                && effectivePort(request.getScheme(), request.getServerPort())
                == effectivePort(uri.getScheme(), uri.getPort());
    }

    private int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
