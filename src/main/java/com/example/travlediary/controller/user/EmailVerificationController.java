package com.example.travlediary.controller.user;

import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.email.EmailVerificationService.ResendOutcome;
import com.example.travlediary.service.email.EmailVerificationService.VerificationOutcome;
import com.example.travlediary.service.email.EmailVerificationService.WaitingState;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.RegistrationValidationException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class EmailVerificationController {

    static final String PENDING_EMAIL_SESSION_ATTRIBUTE = "pendingVerificationEmail";
    static final String PUBLIC_RESEND_MESSAGE =
            "인증이 필요한 계정이라면 입력한 이메일 주소로 인증메일 발송을 요청했습니다. "
                    + "메일함과 스팸함을 확인해주세요.";
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationController.class);

    private final EmailVerificationService emailVerificationService;
    private final MessageSource messageSource;

    public EmailVerificationController(EmailVerificationService emailVerificationService,
                                       MessageSource messageSource) {
        this.emailVerificationService = emailVerificationService;
        this.messageSource = messageSource;
    }

    /** 화면 문구는 현재 locale 의 messages 번들에서 가져온다. */
    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, LocaleContextHolder.getLocale());
    }

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam(value = "token", required = false) String token,
                              HttpSession session,
                              Model model) {
        final VerificationOutcome outcome;
        try {
            outcome = emailVerificationService.verify(token);
        } catch (RuntimeException exception) {
            log.error("Email verification could not be completed: exceptionType={}",
                    exception.getClass().getSimpleName());
            model.addAttribute("verificationStatus", "invalid");
            model.addAttribute("pageTitle", message("verification.error.pageTitle"));
            model.addAttribute("verificationTitle", message("verification.error.title"));
            model.addAttribute("verificationDescription", message("verification.error.description"));
            model.addAttribute("canResend",
                    session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE) != null);
            return "verification-result";
        }
        model.addAttribute("verificationStatus", outcome.status().name().toLowerCase());

        switch (outcome.status()) {
            case SUCCESS -> {
                session.removeAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
                model.addAttribute("pageTitle", message("verification.success.pageTitle"));
                model.addAttribute("verificationTitle", message("verification.success.title"));
                model.addAttribute("verificationDescription",
                        message("verification.success.description"));
            }
            case EXPIRED -> {
                session.setAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE, outcome.email());
                model.addAttribute("pageTitle", message("verification.expired.pageTitle"));
                model.addAttribute("verificationTitle", message("verification.expired.title"));
                model.addAttribute("verificationDescription",
                        message("verification.expired.description"));
            }
            case INVALID -> {
                model.addAttribute("pageTitle", message("verification.invalid.pageTitle"));
                model.addAttribute("verificationTitle", message("verification.invalid.title"));
                model.addAttribute("verificationDescription",
                        message("verification.invalid.description"));
            }
        }
        model.addAttribute("canResend",
                session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE) != null
                        && outcome.status() != EmailVerificationService.VerificationStatus.SUCCESS);
        return "verification-result";
    }

    @GetMapping("/register/verify-waiting")
    public String showVerifyWaitingPage(HttpSession session, Model model) {
        String pendingEmail = (String) session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
        WaitingState waitingState = emailVerificationService.getWaitingState(pendingEmail);
        model.addAttribute("pageTitle", message("verification.waiting.pageTitle"));
        model.addAttribute("verificationAvailable", waitingState.available());
        model.addAttribute("maskedEmail", waitingState.maskedEmail());
        model.addAttribute("cooldownSeconds", waitingState.remainingSeconds());
        return "verify-waiting";
    }

    @GetMapping("/verification/resend")
    public String showStandaloneResendPage(Model model) {
        model.addAttribute("pageTitle", message("verification.resend.pageTitle"));
        return "verification-resend";
    }

    @PostMapping(value = "/verification/resend", params = "!email")
    public String resendSessionVerification(HttpSession session,
                                            RedirectAttributes redirectAttributes) {
        String pendingEmail = (String) session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
        final ResendOutcome outcome;
        try {
            outcome = emailVerificationService.resend(pendingEmail);
        } catch (RuntimeException exception) {
            log.error("Verification resend could not be completed: recipient={}, exceptionType={}",
                    EmailPolicy.mask(pendingEmail), exception.getClass().getSimpleName());
            addMessage(redirectAttributes, "error", message("verification.message.error"));
            return "redirect:/users/register/verify-waiting";
        }

        switch (outcome.status()) {
            case SENT -> addMessage(redirectAttributes, "success",
                    message("verification.message.resent"));
            // 남은 시간은 언어별 어순을 위해 message parameter 로 넘긴다.
            case COOLDOWN -> addMessage(redirectAttributes, "info",
                    message("verification.message.cooldown", outcome.remainingSeconds()));
            case DELIVERY_FAILED -> addMessage(redirectAttributes, "error",
                    message("verification.message.error"));
            case NOT_ELIGIBLE -> addMessage(redirectAttributes, "info",
                    message("verification.message.maybeRequested"));
        }
        return "redirect:/users/register/verify-waiting";
    }

    @PostMapping(value = "/verification/resend", params = "email")
    public String requestStandaloneResend(@RequestParam String email,
                                          RedirectAttributes redirectAttributes) {
        final String normalizedEmail;
        try {
            normalizedEmail = EmailPolicy.normalizeAndValidate(email);
        } catch (RegistrationValidationException exception) {
            redirectAttributes.addFlashAttribute("submittedEmail", safeInput(email));
            redirectAttributes.addFlashAttribute(
                    "emailError", message("verification.error.emailInvalid"));
            return "redirect:/users/verification/resend";
        }

        try {
            emailVerificationService.resend(normalizedEmail);
        } catch (RuntimeException exception) {
            log.error("Standalone verification resend could not be completed: recipient={}, exceptionType={}",
                    EmailPolicy.mask(normalizedEmail), exception.getClass().getSimpleName());
        }

        addMessage(redirectAttributes, "success", message("verification.message.requested"));
        return "redirect:/users/verification/resend";
    }

    private String safeInput(String email) {
        if (email == null) {
            return "";
        }
        String stripped = email.strip();
        return stripped.length() <= 100 ? stripped : stripped.substring(0, 100);
    }

    private void addMessage(RedirectAttributes redirectAttributes, String type, String message) {
        redirectAttributes.addFlashAttribute("verificationMessageType", type);
        redirectAttributes.addFlashAttribute("verificationMessage", message);
    }
}
