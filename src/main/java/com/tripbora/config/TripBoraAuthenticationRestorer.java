package com.tripbora.config;

import com.tripbora.model.User;
import com.tripbora.model.UserStatus;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class TripBoraAuthenticationRestorer {

    private final UserMapper userMapper;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public TripBoraAuthenticationRestorer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public boolean restore(HttpServletRequest request,
                           HttpServletResponse response,
                           Long userId) {
        User user = userId == null ? null : userMapper.findById(userId);
        if (user == null || user.getId() == null || user.getUserRole() == null
                || (user.getStatus() != UserStatus.ACTIVE
                && user.getStatus() != UserStatus.RESTRICTED)) {
            return false;
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession().setAttribute("userId", userId);
        securityContextRepository.saveContext(context, request, response);
        return true;
    }
}
