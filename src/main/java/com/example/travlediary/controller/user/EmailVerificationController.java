package com.example.travlediary.controller.user;

import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.user.EmailCorrectionService;
import com.example.travlediary.service.email.EmailVerificationService.ResendOutcome;
import com.example.travlediary.service.email.EmailVerificationService.VerificationOutcome;
import com.example.travlediary.service.email.EmailVerificationService.VerificationProgress;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/users")
public class EmailVerificationController {

    /** 인증 대기 화면과 재발송이 함께 보는 세션 값. 일반 가입과 소셜 가입이 같은 값을 쓴다. */
    public static final String PENDING_EMAIL_SESSION_ATTRIBUTE = "pendingVerificationEmail";
    static final String PUBLIC_RESEND_MESSAGE =
            "인증이 필요한 계정이라면 입력한 이메일 주소로 인증메일 발송을 요청했습니다. "
                    + "메일함과 스팸함을 확인해주세요.";
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationController.class);

    private final EmailVerificationService emailVerificationService;
    private final EmailCorrectionService emailCorrectionService;
    private final MessageSource messageSource;

    public EmailVerificationController(EmailVerificationService emailVerificationService,
                                       EmailCorrectionService emailCorrectionService,
                                       MessageSource messageSource) {
        this.emailVerificationService = emailVerificationService;
        this.emailCorrectionService = emailCorrectionService;
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
                // 여기서 대기 문맥을 지우지 않는다. 같은 브라우저의 다른 탭이 아직 인증 완료를
                // 감지하지 못했을 수 있고, 세션은 탭끼리 공유되기 때문이다.
                // 정리는 /verification/status 가 VERIFIED 를 돌려줄 때와 로그인 성공 시에 한다.
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
        // 재발송 UI 는 아직 인증 대기 중일 때만 열지만, 진행 상태 확인은 세션이 기다리는 이메일이
        // 있으면 언제나 돈다. 이미 인증이 끝난 뒤 이 화면을 새로고침해도 곧바로 알아채기 위해서다.
        model.addAttribute("verificationPollingAvailable", pendingEmail != null);
        model.addAttribute("maskedEmail", waitingState.maskedEmail());
        model.addAttribute("cooldownSeconds", waitingState.remainingSeconds());
        addEmailCorrectionEntry(model, pendingEmail);
        return "verify-waiting";
    }

    /**
     * 인증 대기 화면이 5초마다 묻는 진행 상태.
     *
     * <p>확인 대상은 오직 이 세션이 기다리고 있는 이메일이다. 요청 파라미터로 다른 회원의 상태를
     * 조회할 수 없고, 응답도 PENDING/VERIFIED/UNKNOWN 세 가지로만 좁힌다.
     */
    @GetMapping("/verification/status")
    @ResponseBody
    public Map<String, String> verificationStatus(HttpSession session) {
        Object pendingEmail = session.getAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
        if (!(pendingEmail instanceof String email)) {
            return Map.of("status", VerificationProgress.UNKNOWN.name());
        }
        final VerificationProgress progress;
        try {
            progress = emailVerificationService.checkProgress(email);
        } catch (RuntimeException exception) {
            log.error("Verification status could not be checked: exceptionType={}",
                    exception.getClass().getSimpleName());
            return Map.of("status", VerificationProgress.UNKNOWN.name());
        }
        if (progress == VerificationProgress.VERIFIED) {
            // 인증이 끝났으면 이 세션은 더 기다릴 것이 없다. 재발송 대상에서도 빠진다.
            session.removeAttribute(PENDING_EMAIL_SESSION_ATTRIBUTE);
        }
        return Map.of("status", progress.name());
    }

    /**
     * 정상적인 이메일 인증 대기 계정이면 가입 경로를 가리지 않고 [이메일 주소 변경] 을 연다.
     *
     * <p>판정 근거는 DB 상태 하나뿐이다. 세션은 "지금 어떤 이메일을 기다리는지" 만 알려주므로
     * 세션이 끊겼다 다시 로그인해도 같은 판정이 복원된다.
     *
     * <p>버튼이 보이는 것과 실제 변경 권한은 별개다. 권한은 비밀번호 재확인이나
     * 소셜 재인증을 마쳐야 생긴다.
     */
    private void addEmailCorrectionEntry(Model model, String pendingEmail) {
        EmailCorrectionService.CorrectionOptions options =
                emailCorrectionService.optionsFor(pendingEmail);
        if (options == null || !options.available()) {
            return;
        }
        model.addAttribute("emailCorrectionAvailable", true);
        model.addAttribute("emailCorrectionPasswordAvailable", options.passwordAvailable());
        model.addAttribute("emailCorrectionProviders", options.providers());
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
