// src/main/java/com/example/travlediary/config/GlobalModelAttributes.java
package com.example.travlediary.config;

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
            model.addAttribute("currentUserProfileImage",
                    normalizeProfileImage(userMapper.findProfileImageByUsername(auth.getName())));
        }
    }

    private String normalizeProfileImage(String profileImage) {
        if (profileImage == null || profileImage.isBlank()) {
            return "/images/default.png";
        }
        String normalized = profileImage.trim();
        if (normalized.equals("uploads/default.png")
                || normalized.equals("/uploads/default.png")
                || normalized.equals("/images/default-profile.png")) {
            return "/images/default.png";
        }
        if (normalized.startsWith("uploads/")) {
            normalized = "/" + normalized;
        }
        return normalized.startsWith("/uploads/") ? normalized : "/images/default.png";
    }
}
