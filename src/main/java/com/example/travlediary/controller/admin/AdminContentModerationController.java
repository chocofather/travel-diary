package com.example.travlediary.controller.admin;

import com.example.travlediary.config.InternalRedirectValidator;
import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.moderation.ContentModerationService;
import com.example.travlediary.service.moderation.ModerationValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/contents")
@RequiredArgsConstructor
public class AdminContentModerationController {

    private final ContentModerationService contentModerationService;

    @PostMapping("/{targetType}/{targetId:\\d+}/hide")
    public String hide(@PathVariable String targetType,
                       @PathVariable Long targetId,
                       @ModelAttribute ContentModerationForm form,
                       @AuthenticationPrincipal CustomUserDetails admin) {
        try {
            contentModerationService.hide(
                    parseTargetType(targetType), targetId, form, adminId(admin));
        } catch (ModerationValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return redirect(form);
    }

    @PostMapping("/{targetType}/{targetId:\\d+}/restore")
    public String restore(@PathVariable String targetType,
                          @PathVariable Long targetId,
                          @ModelAttribute ContentModerationForm form,
                          @AuthenticationPrincipal CustomUserDetails admin) {
        try {
            contentModerationService.restore(
                    parseTargetType(targetType), targetId, form, adminId(admin));
        } catch (ModerationValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return redirect(form);
    }

    /** 조치 후에는 내부 경로로만 되돌아간다. */
    private String redirect(ContentModerationForm form) {
        String target = InternalRedirectValidator.normalize(form == null ? null : form.getRedirect());
        return "redirect:" + (target == null ? "/" : target);
    }

    private Long adminId(CustomUserDetails admin) {
        return admin == null ? null : admin.getId();
    }

    private ModerationTargetType parseTargetType(String targetType) {
        try {
            return ModerationTargetType.valueOf(targetType.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하지 않는 조치 대상입니다.");
        }
    }
}
