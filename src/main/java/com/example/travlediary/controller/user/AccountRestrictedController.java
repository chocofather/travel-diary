package com.example.travlediary.controller.user;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.UserSanctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 이용제한 회원 안내 화면. 인증된 회원만 접근한다. */
@Controller
@RequiredArgsConstructor
public class AccountRestrictedController {

    private final UserSanctionService userSanctionService;

    @GetMapping("/account/restricted")
    public String restricted(@AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        model.addAttribute("sanction", userSanctionService.getActiveSanction(userDetails.getId()));
        model.addAttribute("pageTitle", "이용제한 안내");
        return "account/restricted";
    }
}
