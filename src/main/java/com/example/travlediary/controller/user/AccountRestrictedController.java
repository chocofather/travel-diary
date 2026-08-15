package com.example.travlediary.controller.user;

import com.example.travlediary.dto.AppealForm;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AppealValidationException;
import com.example.travlediary.service.user.UserAppealService;
import com.example.travlediary.service.user.UserSanctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** 이용제한 회원 안내 화면. 인증된 회원만 접근한다. */
@Controller
@RequiredArgsConstructor
public class AccountRestrictedController {

    private static final String VIEW = "account/restricted";

    private final UserSanctionService userSanctionService;
    private final UserAppealService userAppealService;

    @GetMapping("/account/restricted")
    public String restricted(@AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        prepareModel(model, userDetails.getId(), null, null);
        return VIEW;
    }

    /** 이의제기 접수. 대상 제재는 로그인한 회원의 현재 제재에서 서버가 찾는다. */
    @PostMapping("/account/restricted/appeals")
    public String submitAppeal(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @ModelAttribute("appealForm") AppealForm form,
                               Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            userAppealService.submit(userDetails.getId(), form.getContent());
        } catch (AppealValidationException exception) {
            prepareModel(model, userDetails.getId(), form, exception.getMessage());
            return VIEW;
        }
        return "redirect:/account/restricted?appealed=true";
    }

    private void prepareModel(Model model, Long userId, AppealForm form, String errorMessage) {
        UserSanction sanction = userSanctionService.getActiveSanction(userId);
        model.addAttribute("sanction", sanction);
        model.addAttribute("appeal", userAppealService.getLatestAppeal(
                sanction == null ? null : sanction.getId()));
        model.addAttribute("appealForm", form == null ? new AppealForm() : form);
        model.addAttribute("appealError", errorMessage);
        model.addAttribute("pageTitle", "이용제한 안내");
    }
}
