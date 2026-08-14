package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.RestrictedAccountFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserMapper userMapper;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        // 1) 세션에 userId 저장
        User user = userMapper.findByUsername(authentication.getName());
        request.getSession().setAttribute("userId", user.getId());

        // 2) 이용제한 회원은 저장된 요청보다 제한 안내 화면을 우선한다.
        if (user.getStatus() == UserStatus.RESTRICTED) {
            requestCache.removeRequest(request, response);
            response.sendRedirect(RestrictedAccountFilter.RESTRICTED_PATH);
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
