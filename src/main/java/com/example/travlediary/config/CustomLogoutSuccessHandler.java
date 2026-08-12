package com.example.travlediary.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Pattern PUBLIC_NUMERIC_DETAIL = Pattern.compile(
            "^/(?:destinations|travel-info|post|course|events|users)/[0-9]+$");
    private static final Pattern PUBLIC_NOTICE_DETAIL = Pattern.compile(
            "^/support/notices/[0-9]+$");

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String redirect = InternalRedirectValidator.normalize(request.getParameter("redirect"));
        response.sendRedirect(isPublicGetPage(redirect) ? redirect : "/");
    }

    private boolean isPublicGetPage(String redirect) {
        if (redirect == null) {
            return false;
        }

        String path = URI.create(redirect).getPath();
        return "/".equals(path)
                || "/home".equals(path)
                || "/destinations".equals(path)
                || "/travel-info".equals(path)
                || "/support/notices".equals(path)
                || "/support/faq".equals(path)
                || "/board/list".equals(path)
                || "/events".equals(path)
                || "/search".equals(path)
                || "/search.html".equals(path)
                || path.startsWith("/category/")
                || PUBLIC_NUMERIC_DETAIL.matcher(path).matches()
                || PUBLIC_NOTICE_DETAIL.matcher(path).matches();
    }
}
