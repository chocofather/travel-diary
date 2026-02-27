package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserMapper userMapper;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        // 1) 세션에 userId 저장
        User user = userMapper.findByUsername(authentication.getName());
        request.getSession().setAttribute("userId", user.getId());

        // 2) redirect 처리
        String redirect = request.getParameter("redirect");
        if (redirect != null && !redirect.isBlank()) {
            response.sendRedirect(redirect);
        } else {
            response.sendRedirect("/");
        }
    }

}
