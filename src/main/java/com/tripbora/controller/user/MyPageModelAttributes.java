package com.tripbora.controller.user;

import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {MyPageController.class, MyPageAccountController.class})
@RequiredArgsConstructor
public class MyPageModelAttributes {

    private final UserMapper userMapper;

    @ModelAttribute
    public void addMyPageAttributes(Model model, Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return;
        }
        model.addAttribute("hasLocalPassword",
                userMapper.hasLocalPasswordById(userDetails.getId()));
    }
}
