package com.example.travlediary.controller.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.service.email.EmailDeliveryException;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.RegistrationResult;
import com.example.travlediary.service.user.RegistrationValidationException;
import com.example.travlediary.service.user.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
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
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                               BindingResult bindingResult,
                               Authentication authentication,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            clearSensitiveFields(form);
            return "register";
        }

        final RegistrationResult result;
        try {
            result = userService.registerUser(form);
        } catch (RegistrationValidationException exception) {
            log.info("Registration rejected before completion: field={}", exception.getField());
            if ("registration".equals(exception.getField())) {
                bindingResult.reject("registration.duplicate", exception.getMessage());
            } else {
                bindingResult.rejectValue(
                        exception.getField(), "registration.invalid", exception.getMessage());
            }
            clearSensitiveFields(form);
            return "register";
        } catch (RuntimeException exception) {
            log.error("Registration failed before a completion result was returned: exceptionType={}",
                    exception.getClass().getSimpleName());
            bindingResult.reject("registration.failed",
                    "회원가입을 완료할 수 없습니다. 잠시 후 다시 시도해주세요.");
            clearSensitiveFields(form);
            return "register";
        }

        try {
            session.setAttribute(
                    EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE, result.email());
            if (result.verificationEmailRequested()) {
                redirectAttributes.addFlashAttribute("verificationMessageType", "success");
                redirectAttributes.addFlashAttribute(
                        "verificationMessage",
                        "인증메일 발송을 요청했습니다. 잠시 후 메일함을 확인해주세요.");
            } else {
                redirectAttributes.addFlashAttribute("verificationMessageType", "error");
                redirectAttributes.addFlashAttribute("verificationMessage",
                        "회원가입은 완료되었지만 인증메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
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


    // 아이디 찾기
    @GetMapping("/find-username")
    public String showFindUsername() {          // GET  폼
        return "find-username";
    }

    @PostMapping("/find-username")
    public String findUsername(@RequestParam String fullName,
                               @RequestParam String userEmail,
                               RedirectAttributes ra) {  // POST 처리
        try {
            userService.processFindUsername(fullName, userEmail);
            ra.addFlashAttribute("message", "아이디가 이메일로 전송되었습니다.");
        } catch (EmailDeliveryException exception) {
            log.error("Username recovery email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            ra.addFlashAttribute("error",
                    "이메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users/find-username";
    }

    /* ─────────────── 비밀번호 재설정 링크 발송 ─────────────── */

    @GetMapping("/find-password")
    public String showFindPassword() {          // GET  폼
        return "find-password";
    }

    @PostMapping("/find-password")
    public String findPassword(@RequestParam String username,
                               @RequestParam String userEmail,
                               RedirectAttributes ra) {  // POST 처리
        try {
            userService.processResetPasswordRequest(username, userEmail);
            ra.addFlashAttribute("message", "재설정 링크가 이메일로 전송되었습니다.");
        } catch (EmailDeliveryException exception) {
            log.error("Password reset email delivery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            ra.addFlashAttribute("error",
                    "이메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users/find-password";
    }

    /* ─────────────── 토큰 클릭 ⇒ 새 비밀번호 입력 ─────────────── */

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model m) {
        if (userService.validateResetToken(token) == null) {
            return "redirect:/login?error=invalid_token";
        }
        m.addAttribute("token", token);         // hidden 으로 전달
        return "reset-password";
    }

    /* ─────────────── 새 비밀번호 저장 ─────────────── */

    @PostMapping("/reset-password")
    public String doResetPassword(@RequestParam String token,
                                  @RequestParam String newPassword,
                                  RedirectAttributes ra) {
        try {
            userService.resetPassword(token, newPassword);
            ra.addFlashAttribute("resetSuccess", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/login";
    }
}
