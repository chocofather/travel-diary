package com.example.travlediary.controller.user;

import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.ProfileUpdateForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.MyPageService;
import com.example.travlediary.service.user.NicknameCheckStatus;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.ProfileValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping
    public String myPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        model.addAttribute("profile", myPageService.getProfile(userDetails.getId()));
        model.addAttribute("pageTitle", "마이페이지 | 여행일기");
        return "mypage/index";
    }

    @GetMapping("/profile")
    public String profileForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        MyPageProfileDto profile = myPageService.getProfile(userDetails.getId());
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.getNickname());
        prepareProfileModel(model, profile, form);
        return "mypage/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            myPageService.updateProfile(userDetails.getId(), form);
        } catch (ProfileValidationException exception) {
            reject(bindingResult, exception);
            MyPageProfileDto profile = myPageService.getProfile(userDetails.getId());
            prepareProfileModel(model, profile, form);
            return "mypage/profile";
        }

        redirectAttributes.addFlashAttribute("profileMessage", "프로필이 변경되었습니다.");
        return "redirect:/mypage/profile";
    }

    @GetMapping("/profile/check-nickname")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkNickname(
            @RequestParam String nickname,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            NicknameCheckStatus status = myPageService.checkNickname(
                    userDetails.getId(), nickname);
            return ResponseEntity.ok(nicknameCheckResponse(status));
        } catch (NicknamePolicy.ViolationException exception) {
            NicknameCheckStatus status = exception.getViolationType()
                    == NicknamePolicy.ViolationType.FORBIDDEN
                    ? NicknameCheckStatus.FORBIDDEN
                    : NicknameCheckStatus.INVALID_FORMAT;
            return ResponseEntity.badRequest().body(nicknameCheckResponse(status));
        }
    }

    private Map<String, Object> nicknameCheckResponse(NicknameCheckStatus status) {
        return Map.of(
                "status", status.name(),
                "available", status.isAvailable(),
                "message", status.getMessage()
        );
    }

    private void prepareProfileModel(Model model, MyPageProfileDto profile,
                                     ProfileUpdateForm form) {
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", form);
        model.addAttribute("pageTitle", "프로필 변경 | 마이페이지");
    }

    private void reject(BindingResult bindingResult, ProfileValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("profile.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "profile.invalid", exception.getMessage());
    }
}
