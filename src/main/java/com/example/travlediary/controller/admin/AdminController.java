package com.example.travlediary.controller.admin;

import com.example.travlediary.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final DestinationService destinationService;

    @GetMapping
    public String adminHome(Model model) {
        model.addAttribute("pageTitle", "관리자 홈");
        // ↳ layout/main.html 의 <title>이 이 값으로 대체됩니다.
        return "admin/index";
    }


}
