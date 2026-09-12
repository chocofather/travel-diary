package com.example.travlediary.controller.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialSignupAuthenticationException;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupFlowException;
import com.example.travlediary.service.user.SocialSignupPersistenceException;
import com.example.travlediary.service.user.SocialEmailAccountResolver;
import com.example.travlediary.service.user.SocialEmailAccountResolver.EnteredEmailStatus;
import com.example.travlediary.service.user.SocialSignupOutcome;
import com.example.travlediary.service.user.SocialSignupService;
import com.example.travlediary.service.user.SocialSignupValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SocialSignupController {

    private static final String EXPIRED_REDIRECT =
            "redirect:/login?socialSignupExpired=true";

    private static final Logger log = LoggerFactory.getLogger(SocialSignupController.class);

    private final SocialSignupService socialSignupService;
    private final SocialSignupAuthenticationService authenticationService;
    private final SocialEmailAccountResolver socialEmailAccountResolver;
    private final MessageSource messageSource;

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

        model.addAttribute("socialSignupForm", prefilledForm(pending));
        addReferenceInformation(model, pending);
        return "social-signup";
    }

    /**
     * Kakao/Naver 이메일 입력 칸의 상태 확인.
     *
     * <p>세션에 살아 있는 소셜 가입 문맥이 있을 때만 답한다. 이 화면 밖에서 이메일 존재 여부를
     * 캐낼 수 없도록 하기 위해서다. 최종 판정은 언제나 가입 POST 가 다시 한다.
     */
    @GetMapping("/social-signup/email-status")
    @ResponseBody
    public Map<String, String> checkEmailStatus(
            @RequestParam(name = "email", required = false) String email,
            HttpSession session) {
        PendingSocialSignup pending = validPending(session);
        if (pending == null || pending.provider() == SocialProvider.GOOGLE) {
            return Map.of("status", "UNKNOWN");
        }
        if (email == null || email.isBlank()) {
            return Map.of("status", EnteredEmailStatus.INVALID.name());
        }
        return Map.of("status", socialEmailAccountResolver
                .classifyEnteredEmail(email).status().name());
    }

    @PostMapping("/social-signup")
    public String completeSignup(
            @Valid @ModelAttribute("socialSignupForm") SocialSignupForm form,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
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

        final SocialSignupOutcome outcome;
        try {
            outcome = socialSignupService.complete(pending, form);
        } catch (SocialSignupValidationException exception) {
            // messageCode 가 있으면 현재 locale 의 messages 번들에서 문구를 찾는다.
            String messageCode = exception.getMessageCode();
            bindingResult.rejectValue(exception.getField(),
                    messageCode == null ? "socialSignup.invalid" : messageCode,
                    exception.getMessage());
            return signupForm(model, form, pending);
        } catch (SocialSignupFlowException exception) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        } catch (SocialSignupPersistenceException | DataAccessException exception) {
            bindingResult.reject(
                    "signup.social.saveFailed",
                    "가입 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
            return signupForm(model, form, pending);
        }

        clearPending(session);

        // Travel Diary 이메일 인증이 필요한 가입은 자동 로그인하지 않는다.
        // 일반 회원가입과 똑같이 인증 대기 화면으로 보낸다.
        if (outcome.requiresEmailVerification()) {
            return redirectToVerificationWaiting(outcome, session, redirectAttributes);
        }

        try {
            authenticationService.authenticate(outcome.userId(), request, response);
            return null;
        } catch (SocialSignupAuthenticationException exception) {
            return "redirect:/login?socialSignupError=true";
        }
    }

    /** 인증메일 발송과 안내 문구는 일반 회원가입과 같은 흐름·같은 화면을 그대로 쓴다. */
    private String redirectToVerificationWaiting(SocialSignupOutcome outcome,
                                                 HttpSession session,
                                                 RedirectAttributes redirectAttributes) {
        boolean requested;
        try {
            requested = socialSignupService.sendVerificationEmail(outcome);
        } catch (RuntimeException exception) {
            // 발송에 실패해도 가입은 되돌리지 않는다. 재발송 화면에서 다시 받을 수 있다.
            log.error("Social signup verification email dispatch was rejected: "
                            + "userId={}, exceptionType={}",
                    outcome.userId(), exception.getClass().getSimpleName());
            requested = false;
        }
        session.setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                outcome.userEmail());
        redirectAttributes.addFlashAttribute(
                "verificationMessageType", requested ? "success" : "error");
        redirectAttributes.addFlashAttribute("verificationMessage",
                message(requested
                        ? "verification.message.sent" : "verification.message.sendFailed"));
        return "redirect:/users/register/verify-waiting";
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /** Naver 처럼 provider 가 연락처 이메일을 준 경우 입력 칸의 초기값으로 채워 준다. */
    private SocialSignupForm prefilledForm(PendingSocialSignup pending) {
        SocialSignupForm form = new SocialSignupForm();
        if (pending.provider() != SocialProvider.GOOGLE) {
            form.setUserEmail(pending.providerEmail());
        }
        return form;
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
        // Google 은 provider 가 인증한 이메일을 쓰므로 입력 칸을 띄우지 않는다.
        model.addAttribute("emailVerificationRequired",
                pending.provider() != SocialProvider.GOOGLE);
    }

    /** 브랜드명은 번역하지 않고 마이페이지와 같은 provider key 를 그대로 재사용한다. */
    private String providerDisplayName(SocialProvider provider) {
        return messageSource.getMessage(
                "mypage.account.social.provider." + provider.name(),
                null, LocaleContextHolder.getLocale());
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
