// src/main/java/com/example/travlediary/config/GlobalModelAttributes.java
package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalModelAttributes {

    private final UserMapper userMapper;

    public GlobalModelAttributes(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model, Authentication auth) {
        boolean isLoggedIn = auth != null && auth.isAuthenticated();
        model.addAttribute("isLoggedIn", isLoggedIn);

        if (isLoggedIn) {
            User user = userMapper.findByUsername(auth.getName());
            model.addAttribute("user", user);
        }
    }
}
