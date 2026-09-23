package com.example.travlediary.controller;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.seo.SeoStructuredData;
import com.example.travlediary.service.course.CourseService;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CourseService courseService;

    public HomeController(CourseService courseService) {
     this.courseService = courseService;
    }

    @GetMapping("/")
    public String home(Model model, Authentication auth) {
        /*
          예전에는 여기서 회원 한 줄을 통째로 읽어 "user" 로 넘겼는데 home.html 이 쓰지 않는다.
          로그인 여부와 헤더 프로필 사진은 GlobalModelAttributes 가 이미 모든 화면에 넣어 준다.
        */
        model.addAttribute("isLoggedIn", authenticatedUser(auth) != null);
        model.addAttribute("popularCourses", courseService.getPopularCoursesForHome(
                SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                        .orElse(SupportedLanguage.KOREAN)));
        SeoStructuredData.website(model);

        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    private CustomUserDetails authenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails;
    }

}
