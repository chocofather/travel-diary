package com.example.travlediary.controller;

import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.course.CourseService;

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
        boolean isLoggedIn = auth != null && auth.isAuthenticated();
        model.addAttribute("isLoggedIn", isLoggedIn);
        if (isLoggedIn) {
            model.addAttribute("user", userMapper.findByUsername(auth.getName()));
        }
        model.addAttribute("popularCourses", courseService.getPopularCoursesForHome());

        return "home";
    }

}
