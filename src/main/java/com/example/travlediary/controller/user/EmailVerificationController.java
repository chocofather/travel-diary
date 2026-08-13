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

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
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
            model.addAttribute("pageTitle", "이메일 인증 오류");
            model.addAttribute("verificationTitle", "이메일 인증을 처리할 수 없습니다");
            model.addAttribute("verificationDescription", "잠시 후 다시 시도해주세요.");
            model.addAttribute("canResend",
                    session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE) != null);
            return "verification-result";
        }
        model.addAttribute("verificationStatus", outcome.status().name().toLowerCase());

        switch (outcome.status()) {
            case SUCCESS -> {
                session.removeAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
                model.addAttribute("pageTitle", "이메일 인증 완료");
                model.addAttribute("verificationTitle", "이메일 인증이 완료되었습니다");
                model.addAttribute("verificationDescription", "이제 Travel Diary에 로그인할 수 있습니다.");
            }
            case EXPIRED -> {
                session.setAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE, outcome.email());
                model.addAttribute("pageTitle", "인증 링크 만료");
                model.addAttribute("verificationTitle", "인증 링크가 만료되었습니다");
                model.addAttribute("verificationDescription", "새 인증메일을 요청한 뒤 다시 인증해주세요.");
            }
            case INVALID -> {
                model.addAttribute("pageTitle", "유효하지 않은 인증 링크");
                model.addAttribute("verificationTitle", "유효하지 않은 인증 링크입니다");
                model.addAttribute("verificationDescription",
                        "링크가 잘못되었거나 이미 사용되었습니다. 메일의 최신 링크를 확인해주세요.");
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
        model.addAttribute("pageTitle", "이메일 인증 대기");
        model.addAttribute("verificationAvailable", waitingState.available());
        model.addAttribute("maskedEmail", waitingState.maskedEmail());
        model.addAttribute("cooldownSeconds", waitingState.remainingSeconds());
        return "verify-waiting";
    }

    @GetMapping("/verification/resend")
    public String showStandaloneResendPage(Model model) {
        model.addAttribute("pageTitle", "인증메일 다시 받기");
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
            addMessage(redirectAttributes, "error",
                    "인증메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/users/register/verify-waiting";
        }

        switch (outcome.status()) {
            case SENT -> addMessage(redirectAttributes, "success",
                    "인증메일 재발송을 요청했습니다. 잠시 후 최신 메일의 링크를 사용해주세요.");
            case COOLDOWN -> addMessage(redirectAttributes, "info",
                    outcome.remainingSeconds() + "초 후 다시 요청할 수 있습니다.");
            case DELIVERY_FAILED -> addMessage(redirectAttributes, "error",
                    "인증메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
            case NOT_ELIGIBLE -> addMessage(redirectAttributes, "info",
                    "인증이 필요한 계정이라면 인증메일 발송을 요청했습니다.");
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
            redirectAttributes.addFlashAttribute("emailError", EmailPolicy.INVALID_MESSAGE);
            return "redirect:/users/verification/resend";
        }

        try {
            emailVerificationService.resend(normalizedEmail);
        } catch (RuntimeException exception) {
            log.error("Standalone verification resend could not be completed: recipient={}, exceptionType={}",
                    EmailPolicy.mask(normalizedEmail), exception.getClass().getSimpleName());
        }

        addMessage(redirectAttributes, "success", PUBLIC_RESEND_MESSAGE);
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
