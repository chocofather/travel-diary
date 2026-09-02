package com.example.travlediary.controller.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialSignupAuthenticationException;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupFlowException;
import com.example.travlediary.service.user.SocialSignupPersistenceException;
import com.example.travlediary.service.user.SocialSignupService;
import com.example.travlediary.service.user.SocialSignupValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class SocialSignupController {

    private static final String EXPIRED_REDIRECT =
            "redirect:/login?socialSignupExpired=true";

    private final SocialSignupService socialSignupService;
    private final SocialSignupAuthenticationService authenticationService;

    @GetMapping("/social-signup")
    public String signupPage(Authentication authentication,
                             HttpSession session,
                             Model model) {
        if (isTravelDiaryMember(authentication)) {
            return "redirect:/";
        }

        PendingSocialSignup pending = validPending(session);
        if (pending == null) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        }

        model.addAttribute("socialSignupForm", new SocialSignupForm());
        addReferenceInformation(model, pending);
        return "social-signup";
    }

    @PostMapping("/social-signup")
    public String completeSignup(
            @Valid @ModelAttribute("socialSignupForm") SocialSignupForm form,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) throws IOException {
        if (isTravelDiaryMember(authentication)) {
            return "redirect:/";
        }

        HttpSession session = request.getSession();
        PendingSocialSignup pending = validPending(session);
        if (pending == null) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        }

        if (bindingResult.hasErrors()) {
            return signupForm(model, form, pending);
        }

        final long userId;
        try {
            userId = socialSignupService.complete(pending, form);
        } catch (SocialSignupValidationException exception) {
            bindingResult.rejectValue(
                    exception.getField(), "socialSignup.invalid", exception.getMessage());
            return signupForm(model, form, pending);
        } catch (SocialSignupFlowException exception) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        } catch (SocialSignupPersistenceException | DataAccessException exception) {
            bindingResult.reject(
                    "socialSignup.saveFailed",
                    "가입 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
            return signupForm(model, form, pending);
        }

        try {
            authenticationService.authenticate(userId, request, response);
            return null;
        } catch (SocialSignupAuthenticationException exception) {
            clearPending(session);
            return "redirect:/login?socialSignupError=true";
        }
    }

    private String signupForm(Model model,
                              SocialSignupForm form,
                              PendingSocialSignup pending) {
        model.addAttribute("socialSignupForm", form);
        addReferenceInformation(model, pending);
        return "social-signup";
    }

    private void addReferenceInformation(Model model, PendingSocialSignup pending) {
        model.addAttribute("provider", pending.provider());
        model.addAttribute("providerDisplayName", providerDisplayName(pending.provider()));
        model.addAttribute("providerEmail", pending.providerEmail());
    }

    private String providerDisplayName(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
        };
    }

    private PendingSocialSignup validPending(HttpSession session) {
        Object value = session.getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialSignup pending)
                || !isSupportedProvider(pending.provider())
                || isBlank(pending.flowId())
                || isBlank(pending.providerUserId())
                || pending.createdAt() == null
                || pending.isExpired(Instant.now())) {
            return null;
        }
        return pending;
    }

    private boolean isSupportedProvider(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE
                || provider == SocialProvider.KAKAO
                || provider == SocialProvider.NAVER;
    }

    private boolean isTravelDiaryMember(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails;
    }

    private void clearPending(HttpSession session) {
        session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
