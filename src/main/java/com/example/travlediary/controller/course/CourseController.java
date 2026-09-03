package com.example.travlediary.controller.course;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseUpdateRequest;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.course.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/course")
public class CourseController {

    private final CourseService courseService;
    private final CountryCategoryService countryCategoryService;

    @GetMapping("/{id}")
    public String courseDetail(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               Model model) {
        Long currentUserId = principal == null ? null : principal.getId();
        model.addAttribute("course",
                courseService.getCourseDetail(id, currentUserId, requestedLanguage()));
        return "course/detail";
    }

    @GetMapping("/{id}/edit")
    public String courseEditPage(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 Model model) {
        model.addAttribute("course",
                courseService.getCourseForEdit(id, principal.getId(), requestedLanguage()));
        addCourseCountries(model);
        return "course/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateCourse(@PathVariable Long id,
                               @ModelAttribute CourseUpdateRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        courseService.updateCourse(id, principal.getId(), request);
        return "redirect:/course/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteCourse(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        courseService.deleteCourse(id, principal.getId());
        return "redirect:/board/list";
    }

    // 글쓰기 폼 페이지 (GET)
    @GetMapping("/write")
    public String courseWritePage(Model model) {
        addCourseCountries(model);
        return "course/write";
    }

    /** 화면에 쓸 언어. 코스 STOP 이름만 이 언어로 내려간다. */
    private SupportedLanguage requestedLanguage() {
        return SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
    }

    private void addCourseCountries(Model model) {
        List<CountryCategory> countries = countryCategoryService.getCourseCountries();
        model.addAttribute("domesticCourseCountries", countries.stream()
                .filter(country -> country.getParentId() == null)
                .toList());
        model.addAttribute("overseasCourseCountries", countries.stream()
                .filter(country -> country.getParentId() != null)
                .toList());
    }

    // 글쓰기 저장 (POST)
    @PostMapping("/write")
    public String submitCourse(
            @ModelAttribute CourseCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long courseId = courseService.createCourse(request, principal.getId());
        return "redirect:/course/" + courseId;
    }
}
