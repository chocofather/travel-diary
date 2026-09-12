package com.example.travlediary.config;

import com.example.travlediary.controller.user.EmailVerificationController;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.security.ExpiredWithdrawalResponse;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.RestrictedAccountFilter;
import com.example.travlediary.security.WithdrawalPendingAccountFilter;
import com.example.travlediary.service.user.SocialLoginLinkService;
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

    /** 소셜 연결 결과 안내가 렌더링되는 화면. 마이페이지 계정 및 보안과 같은 자리를 쓴다. */
    static final String SOCIAL_LINK_RESULT_PATH = "/mypage/account";

    private final UserMapper userMapper;
    private final LoginThrottle loginThrottle;
    private final WithdrawalGraceService withdrawalGraceService;
    private final SocialLoginLinkService socialLoginLinkService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Autowired
    public CustomLoginSuccessHandler(UserMapper userMapper,
                                     LoginThrottle loginThrottle,
                                     WithdrawalGraceService withdrawalGraceService,
                                     SocialLoginLinkService socialLoginLinkService) {
        this.userMapper = userMapper;
        this.loginThrottle = loginThrottle;
        this.withdrawalGraceService = withdrawalGraceService;
        this.socialLoginLinkService = socialLoginLinkService;
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
            // 이메일 인증 대기 문맥은 인증 링크가 아니라 여기서 닫는다. 인증 화면에서 바로 지우면
            // 같은 브라우저의 대기 탭이 완료를 감지하지 못한다.
            request.getSession(false).removeAttribute(
                    EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE);
        }

        // 1) 인증 완료 후에는 username 이 아니라 DB 회원 ID를 세션 식별값으로 사용한다.
        request.getSession().setAttribute("userId", userId);

        // 1-1) 기존 계정 로그인을 기다리던 소셜 연결이 있으면 여기서 마무리한다.
        //      일반 로그인과 소셜 로그인이 같은 서비스를 쓴다. 아래 상태 격리보다 먼저 부르는 이유는
        //      대기 문맥을 상태와 무관하게 반드시 소비해 세션에 남기지 않기 위해서다.
        SocialLoginLinkService.Outcome linkOutcome =
                socialLoginLinkService.completeAfterLogin(request.getSession(), userId);

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

        // 2-1) 소셜 연결을 시도했다면 결과 안내가 보이는 계정 화면으로 보낸다.
        //      기다리던 연결이 없었으면(NONE) 아래 평소 이동 규칙을 그대로 쓴다.
        if (linkOutcome != null && linkOutcome.handled()) {
            requestCache.removeRequest(request, response);
            response.sendRedirect(SOCIAL_LINK_RESULT_PATH);
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
