package com.example.travlediary.controller;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseService;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UserMapper userMapper;
    private final CourseService courseService;

    public HomeController(UserMapper userMapper, CourseService courseService) {
     this.userMapper = userMapper;
     this.courseService = courseService;
    }

    @GetMapping("/")
    public String home(Model model, Authentication auth) {
        CustomUserDetails userDetails = authenticatedUser(auth);
        boolean isLoggedIn = userDetails != null;
        model.addAttribute("isLoggedIn", isLoggedIn);
        if (isLoggedIn) {
            model.addAttribute("user", userMapper.findById(userDetails.getId()));
        }
        model.addAttribute("popularCourses", courseService.getPopularCoursesForHome(
                SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                        .orElse(SupportedLanguage.KOREAN)));

        return "home";
    }

    private CustomUserDetails authenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails;
    }

}
