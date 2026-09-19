package com.example.travlediary.controller.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.security.AccountAbuseGuard;
import com.example.travlediary.security.ClientIpResolver;
import com.example.travlediary.security.TooManyAccountRequestsException;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.PasswordPolicy;
import com.example.travlediary.service.user.RegistrationResult;
import com.example.travlediary.service.user.RegistrationValidationException;
import com.example.travlediary.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final MessageSource messageSource;
    private final SignupPolicyService signupPolicyService;
    /** 메일이 나갈 수 있는 요청의 남용을 막는 자리. 한도는 이 Controller 가 알지 않는다. */
    private final AccountAbuseGuard accountAbuseGuard;

    @Autowired
    public UserController(UserService userService, MessageSource messageSource,
                          SignupPolicyService signupPolicyService,
                          AccountAbuseGuard accountAbuseGuard) {
        this.userService = userService;
        this.messageSource = messageSource;
        this.signupPolicyService = signupPolicyService;
        this.accountAbuseGuard = accountAbuseGuard;
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, LocaleContextHolder.getLocale());
    }

    // 회원가입 폼 화면
    @GetMapping("/register")
    public String showRegisterForm(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return registerForm(model);
    }

    /**
     * 회원가입 화면. 약관 항목은 DB 의 현재 정책 세트가 정한다.
     * 아직 활성화하지 않은 정책은 세트에 들어오지 않아 화면에도 나오지 않는다.
     */
    private String registerForm(Model model) {
        model.addAttribute("signupPolicies", signupPolicyService.loadSignupPolicies());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                               BindingResult bindingResult,
                               Authentication authentication,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            clearSensitiveFields(form);
            return registerForm(model);
        }

        /*
          가입이 끝나면 인증메일이 나간다. 주소를 바꿔 가며 가입을 되풀이하면 SMTP 한도가
          바닥나고, 그 순간 정상 회원의 비밀번호 재설정 메일까지 함께 막힌다.
          그래서 복구 메일과 같은 IP 통에서 함께 센다 — 쓰는 SMTP 자원이 하나이기 때문이다.

          입력 형식 오류는 메일과 무관하므로 위 검사를 지난 뒤에 센다.
        */
        try {
            accountAbuseGuard.checkRecoveryRequest(ClientIpResolver.of(request));
        } catch (TooManyAccountRequestsException exception) {
            clearSensitiveFields(form);
            rejectAsThrottled(exception, response, bindingResult);
            return registerForm(model);
        }

        final RegistrationResult result;
        try {
            result = userService.registerUser(form);
        } catch (RegistrationValidationException exception) {
            log.info("Registration rejected before completion: field={}", exception.getField());
            // messageCode 를 넘기면 현재 locale 의 messages 번들에서 문구를 찾고,
            // 없으면 기존 메시지를 그대로 쓴다.
            String messageCode = exception.getMessageCode();
            if ("registration".equals(exception.getField())) {
                bindingResult.reject(
                        messageCode == null ? "registration.duplicate" : messageCode,
                        exception.getMessage());
            } else {
                bindingResult.rejectValue(exception.getField(),
                        messageCode == null ? "registration.invalid" : messageCode,
                        exception.getMessage());
            }
            clearSensitiveFields(form);
            return registerForm(model);
        } catch (RuntimeException exception) {
            log.error("Registration failed before a completion result was returned: exceptionType={}",
                    exception.getClass().getSimpleName());
            bindingResult.reject("signup.error.failed",
                    "회원가입을 완료할 수 없습니다. 잠시 후 다시 시도해주세요.");
            clearSensitiveFields(form);
            return registerForm(model);
        }

        try {
            session.setAttribute(
                    EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE, result.email());
            if (result.verificationEmailRequested()) {
                redirectAttributes.addFlashAttribute("verificationMessageType", "success");
                redirectAttributes.addFlashAttribute(
                        "verificationMessage", message("verification.message.sent"));
            } else {
                redirectAttributes.addFlashAttribute("verificationMessageType", "error");
                redirectAttributes.addFlashAttribute(
                        "verificationMessage", message("verification.message.sendFailed"));
            }
            log.info("Registration completed; redirecting to verification waiting: recipient={}, emailRequested={}",
                    EmailPolicy.mask(result.email()),
                    result.verificationEmailRequested());
            return "redirect:/users/register/verify-waiting";
        } catch (RuntimeException exception) {
            log.error("Registration was completed but verification redirect preparation failed: "
                            + "recipient={}, exceptionType={}",
                    EmailPolicy.mask(result.email()),
                    exception.getClass().getSimpleName());
            return "redirect:/users/verification/resend";
        }
    }

    private void clearSensitiveFields(RegistrationForm form) {
        form.setUserPassword(null);
        form.setPasswordConfirm(null);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }


    /* ─────────────── 비밀번호 재설정 링크 발송 ─────────────── */

    @GetMapping("/find-password")
    public String showFindPassword() {          // GET  폼
        return "find-password";
    }

    /** 비밀번호 재설정 링크 요청. 계정 존재 여부와 관계없이 같은 응답을 보낸다. */
    @PostMapping("/find-password")
    public String findPassword(@RequestParam String userEmail,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model,
                               RedirectAttributes ra) {  // POST 처리
        try {
            accountAbuseGuard.checkRecoveryRequest(ClientIpResolver.of(request));
        } catch (TooManyAccountRequestsException exception) {
            return throttledRecoveryView(exception, response, model, "find-password");
        }

        try {
            userService.processResetPasswordRequest(userEmail);
        } catch (RuntimeException exception) {
            log.error("Password recovery request could not be completed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
        ra.addFlashAttribute("recoveryRequested", true);
        return "redirect:/users/find-password";
    }

    /**
     * 요청이 너무 많을 때 보여 주는 화면.
     *
     * <p>흐름을 끊지 않도록 원래 폼을 그대로 다시 그리고 안내만 얹는다. 상태 코드는 429 이고
     * 다시 시도할 수 있는 시각만 알려 준다 — 계정이 있는지, 어떤 한도에 걸렸는지는 담지 않는다.
     */
    private String throttledRecoveryView(TooManyAccountRequestsException exception,
                                         HttpServletResponse response,
                                         Model model,
                                         String viewName) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        model.addAttribute("recoveryThrottled", true);
        model.addAttribute("recoveryRetryAfterSeconds", exception.getRetryAfterSeconds());
        model.addAttribute("recoveryThrottledMessage",
                message("account.recovery.throttled.description",
                        exception.getRetryAfterSeconds()));
        return viewName;
    }

    /**
     * 가입 폼이 요청 한도에 걸렸을 때의 표시.
     *
     * <p>가입 화면은 이미 전체 오류를 보여 주는 자리가 있으므로 그 자리에 안내를 얹는다.
     * 계정이 있는지나 어떤 한도인지는 담지 않고, 다시 시도할 수 있는 시각만 알려 준다.
     */
    private void rejectAsThrottled(TooManyAccountRequestsException exception,
                                   HttpServletResponse response,
                                   BindingResult bindingResult) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        bindingResult.reject("account.recovery.throttled.description",
                new Object[]{exception.getRetryAfterSeconds()},
                "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    /* ─────────────── 토큰 클릭 ⇒ 새 비밀번호 입력 ─────────────── */

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model m) {
        if (userService.validateResetToken(token) == null) {
            return "redirect:/login?error=invalid_token";
        }
        m.addAttribute("token", token);         // hidden 으로 전달
        // 비밀번호 정책 문구는 회원가입과 같은 key 를 그대로 쓴다.
        m.addAttribute("passwordPolicyMessage", message("signup.error.password.policy"));
        return "reset-password";
    }

    /* ─────────────── 새 비밀번호 저장 ─────────────── */

    @PostMapping("/reset-password")
    public String doResetPassword(@RequestParam String token,
                                  @RequestParam String newPassword,
                                  @RequestParam String newPasswordConfirm,
                                  RedirectAttributes ra) {
        try {
            userService.resetPassword(token, newPassword, newPasswordConfirm);
            return "redirect:/login?passwordChanged=true";
        } catch (IllegalArgumentException exception) {
            if (UserService.INVALID_RESET_TOKEN_MESSAGE.equals(exception.getMessage())) {
                return "redirect:/login?error=invalid_token";
            }
            ra.addFlashAttribute("error", resetPasswordError(exception.getMessage()));
            ra.addAttribute("token", token);
            return "redirect:/users/reset-password";
        }
    }

    /**
     * 재설정 화면에 보여줄 오류 문구를 현재 locale 로 맞춘다.
     * 검증 규칙은 그대로 두고, 정책 상수와 같은 메시지만 기존 message key 로 바꿔 준다.
     */
    private String resetPasswordError(String rawMessage) {
        if (PasswordPolicy.INVALID_MESSAGE.equals(rawMessage)) {
            return message("signup.error.password.policy");
        }
        if (PasswordPolicy.MISMATCH_MESSAGE.equals(rawMessage)) {
            return message("mypage.account.error.password.mismatch");
        }
        if (UserService.SAME_AS_CURRENT_PASSWORD_MESSAGE.equals(rawMessage)) {
            return message("password.reset.error.sameAsCurrent");
        }
        return rawMessage;
    }
}
