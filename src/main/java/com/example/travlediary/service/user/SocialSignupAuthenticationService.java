package com.example.travlediary.service.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.model.PendingSocialLink;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SocialSignupAuthenticationService {

    private final UserMapper userMapper;
    private final CustomLoginSuccessHandler customLoginSuccessHandler;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
            new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public void authenticate(Long userId,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        authenticate(userId, request, response, EnumSet.of(UserStatus.ACTIVE));
    }

    /**
     * 소셜 계정을 기존 회원에게 연결한 직후의 로그인.
     *
     * <p>제재 중인 계정도 기존 로그인 정책에서는 인증까지 허용하므로 같은 기준을 쓴다.
     * 격리 화면으로 보내는 일은 {@link CustomLoginSuccessHandler} 가 그대로 맡는다.
     */
    public void authenticateExistingMember(Long userId,
                                           HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        authenticate(userId, request, response,
                EnumSet.of(UserStatus.ACTIVE, UserStatus.RESTRICTED));
    }

    private void authenticate(Long userId,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Set<UserStatus> allowedStatuses) throws IOException {
        User user = userId == null ? null : userMapper.findById(userId);
        if (user == null || user.getId() == null || user.getUserRole() == null
                || !allowedStatuses.contains(user.getStatus())) {
            throw new SocialSignupAuthenticationException(
                    "가입한 회원의 로그인 정보를 확인할 수 없습니다.");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, userDetails.getAuthorities());

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        request.getSession().removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        request.getSession().removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        customLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);
    }
}
