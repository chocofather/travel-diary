package com.example.travlediary.controller.user;

import com.example.travlediary.model.PendingEmailCorrection;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.EmailCorrectionService;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import com.example.travlediary.service.user.MissingEmailRegistrationService.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.Map;

/**
 * 이메일 없이 남아 있는 예전 Kakao/Naver 회원의 이메일 등록 화면.
 *
 * <p>회원가입이 아니라 기존 계정의 이메일 보완이다. 닉네임도 약관도 다시 받지 않고
 * 이메일 하나만 받아 기존 이메일 인증 절차로 넘긴다.
 */
@Controller
@RequestMapping("/account/email-required")
@RequiredArgsConstructor
public class AccountEmailRequiredController {

    private static final String VIEW = "account/email-required";
    private static final String CHANGE_VIEW = "account/email-change";
    private static final String PASSWORD_VIEW = "account/email-change-password";
    private static final String VERIFY_WAITING_REDIRECT =
            "redirect:/users/register/verify-waiting";

    private static final Logger log =
            LoggerFactory.getLogger(AccountEmailRequiredController.class);

    private final MissingEmailRegistrationService missingEmailRegistrationService;
    private final EmailCorrectionService emailCorrectionService;
    private final MessageSource messageSource;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @GetMapping
    public String emailRequiredPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    Model model) {
        if (!isTarget(userDetails)) {
            return "redirect:/";
        }
        if (!model.containsAttribute("enteredEmail")) {
            model.addAttribute("enteredEmail", "");
        }
        model.addAttribute("pageTitle", message("account.emailRequired.pageTitle"));
        return VIEW;
    }

    /**
     * 입력 칸의 상태 확인.
     *
     * <p>이 화면을 실제로 거쳐야 하는 회원에게만 답한다. 다른 회원의 계정 상태는
     * AVAILABLE / UNAVAILABLE / INVALID 뒤로 감춘다.
     */
    @GetMapping("/email-status")
    @ResponseBody
    public Map<String, String> checkEmailStatus(
            @RequestParam(name = "email", required = false) String email,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!isTarget(userDetails)) {
            return Map.of("status", "UNKNOWN");
        }
        if (email == null || email.isBlank()) {
            return Map.of("status",
                    MissingEmailRegistrationService.EmailAvailability.INVALID.name());
        }
        return Map.of("status",
                missingEmailRegistrationService.checkAvailability(email).name());
    }

    @PostMapping
    public String registerEmail(@RequestParam(name = "userEmail", required = false) String email,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        // AJAX 결과를 믿지 않는다. 대상 여부·이메일·계정 상태를 서비스가 모두 다시 확인한다.
        Outcome outcome = missingEmailRegistrationService.start(userDetails.getId(), email);
        if (!outcome.result().started()) {
            return rejected(outcome, userDetails, model, email);
        }

        // 계정은 방금 인증 대기(INACTIVE)로 바뀌었다. 기존 인증 상태를 그대로 들고 다니면
        // 격리를 지나칠 수 있으므로 여기서 끊는다. 세션 자체는 인증 대기 화면이 써야 하므로 남긴다.
        HttpSession session = request.getSession();
        session.removeAttribute("userId");
        clearAuthentication(request, response);
        session.setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                outcome.userEmail());

        boolean sent = outcome.result() == MissingEmailRegistrationService.Result.STARTED;
        redirectAttributes.addFlashAttribute(
                "verificationMessageType", sent ? "success" : "error");
        redirectAttributes.addFlashAttribute("verificationMessage",
                message(sent ? "verification.message.sent" : "verification.message.sendFailed"));
        return "redirect:/users/register/verify-waiting";
    }

    /** 등록을 시작하지 못한 경우. 대상에서 벗어났으면 화면을 닫고, 아니면 사유를 보여준다. */
    private String rejected(Outcome outcome,
                            CustomUserDetails userDetails,
                            Model model,
                            String email) {
        if (outcome.result() == MissingEmailRegistrationService.Result.NOT_ELIGIBLE) {
            log.info("Email registration screen closed for a non-target account: userId={}",
                    userDetails.getId());
            return "redirect:/";
        }
        model.addAttribute("enteredEmail", email == null ? "" : email.strip());
        model.addAttribute("emailError", message(
                outcome.result() == MissingEmailRegistrationService.Result.INVALID_EMAIL
                        ? "signup.error.email.invalid"
                        : "account.emailRequired.email.unavailable"));
        model.addAttribute("pageTitle", message("account.emailRequired.pageTitle"));
        return VIEW;
    }


    /* ---------- 잘못 입력한 이메일 고치기 ---------- */

    /**
     * 소셜 재인증으로 이메일 변경 본인확인 시작.
     *
     * <p>세션의 인증 대기 이메일만으로 변경 권한을 주지 않는다. 여기서는 대상과 provider 만
     * 확정해 문맥으로 남기고, 실제 권한은 그 계정에 연결된 소셜 재인증을 마쳐야 붙는다.
     */
    @PostMapping("/change/start")
    public String beginSocialEmailCorrection(
            @RequestParam(name = "provider", required = false) String registrationId,
            HttpServletRequest request) {
        HttpSession session = request.getSession();
        SocialProvider provider = SocialProvider.fromRegistrationId(registrationId).orElse(null);
        // 세션은 어떤 이메일을 기다리는지만 알려준다. 대상 자격과 연결 여부는 서비스가 DB 로 본다.
        PendingEmailCorrection correction = provider == null ? null
                : emailCorrectionService.beginSocial(pendingEmail(session), provider);
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }

        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE, correction);
        return "redirect:/oauth2/authorization/" + registrationId;
    }

    /**
     * 비밀번호 재확인으로 이메일 변경 본인확인 시작. 비밀번호 입력 화면만 연다.
     * 소셜 전용 계정(비밀번호 없음)은 서비스가 막는다.
     */
    @PostMapping("/change/password/start")
    public String beginLocalEmailCorrection(HttpServletRequest request) {
        HttpSession session = request.getSession();
        PendingEmailCorrection correction =
                emailCorrectionService.beginLocal(pendingEmail(session));
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }

        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE, correction);
        return "redirect:/account/email-required/change/password";
    }

    @GetMapping("/change/password")
    public String passwordCheckPage(HttpSession session, Model model) {
        PendingEmailCorrection correction = localCorrection(session);
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }
        model.addAttribute("maskedCurrentEmail",
                EmailPolicy.mask(correction.expectedCurrentEmail()));
        model.addAttribute("pageTitle", message("account.emailChange.password.pageTitle"));
        return PASSWORD_VIEW;
    }

    /** 비밀번호가 맞을 때만 변경 권한이 붙는다. 로그인 상태를 만들지 않는다. */
    @PostMapping("/change/password")
    public String checkPassword(
            @RequestParam(name = "currentPassword", required = false) String password,
            HttpServletRequest request,
            Model model) {
        HttpSession session = request.getSession();
        PendingEmailCorrection correction = localCorrection(session);
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }

        PendingEmailCorrection authorized =
                emailCorrectionService.authorizeLocal(correction, password);
        if (authorized == null) {
            // 문맥은 남겨 다시 시도할 수 있게 하고, 사유는 일반적인 문구로만 알린다.
            model.addAttribute("maskedCurrentEmail",
                    EmailPolicy.mask(correction.expectedCurrentEmail()));
            model.addAttribute("passwordError", message("account.emailChange.password.invalid"));
            model.addAttribute("pageTitle", message("account.emailChange.password.pageTitle"));
            return PASSWORD_VIEW;
        }

        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE, authorized);
        return "redirect:/account/email-required/change";
    }

    /** 아직 권한이 붙지 않은 비밀번호 확인 문맥. */
    private PendingEmailCorrection localCorrection(HttpSession session) {
        Object value = session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
        return value instanceof PendingEmailCorrection correction
                && correction.method() == PendingEmailCorrection.Method.LOCAL
                && correction.isValidAt(Instant.now()) ? correction : null;
    }

    private String pendingEmail(HttpSession session) {
        Object value =
                session.getAttribute(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE);
        return value instanceof String email ? email : null;
    }

    @GetMapping("/change")
    public String changeEmailPage(HttpSession session, Model model) {
        PendingEmailCorrection correction = authorizedCorrection(session);
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }
        addCorrectionReference(model, correction);
        return CHANGE_VIEW;
    }

    /** 새 이메일 입력 칸의 상태 확인. 본인확인을 마친 문맥이 있을 때만 답한다. */
    @GetMapping("/change/email-status")
    @ResponseBody
    public Map<String, String> checkNewEmailStatus(
            @RequestParam(name = "email", required = false) String email,
            HttpSession session) {
        if (authorizedCorrection(session) == null) {
            return Map.of("status", "UNKNOWN");
        }
        if (email == null || email.isBlank()) {
            return Map.of("status", EmailCorrectionService.Availability.INVALID.name());
        }
        return Map.of("status", emailCorrectionService.checkAvailability(email).name());
    }

    @PostMapping("/change")
    public String changeEmail(@RequestParam(name = "userEmail", required = false) String email,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        HttpSession session = request.getSession();
        PendingEmailCorrection correction = authorizedCorrection(session);
        if (correction == null) {
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }

        // AJAX 결과를 믿지 않는다. 권한·대상 상태·이메일을 서비스가 모두 다시 확인한다.
        EmailCorrectionService.Outcome outcome =
                emailCorrectionService.change(correction, email);
        if (!outcome.result().changed()) {
            return rejectedCorrection(session, correction, outcome, model, email);
        }

        session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
        session.setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                outcome.userEmail());

        boolean sent = outcome.result() == EmailCorrectionService.Result.CHANGED;
        redirectAttributes.addFlashAttribute(
                "verificationMessageType", sent ? "success" : "error");
        redirectAttributes.addFlashAttribute("verificationMessage",
                message(sent ? "verification.message.sent" : "verification.message.sendFailed"));
        return VERIFY_WAITING_REDIRECT;
    }

    /** 취소. 이메일도 토큰도 그대로 두고 문맥만 정리한다. */
    @PostMapping("/change/cancel")
    public String cancelEmailCorrection(HttpSession session) {
        session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
        return VERIFY_WAITING_REDIRECT;
    }

    private String rejectedCorrection(HttpSession session,
                                      PendingEmailCorrection correction,
                                      EmailCorrectionService.Outcome outcome,
                                      Model model,
                                      String email) {
        if (outcome.result() == EmailCorrectionService.Result.NOT_AUTHORIZED) {
            // 권한이 만료됐거나 그사이 대상 상태가 바뀌었다. 처음부터 다시 해야 한다.
            session.removeAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
            return VERIFY_WAITING_REDIRECT;
        }
        model.addAttribute("enteredEmail", email == null ? "" : email.strip());
        model.addAttribute("emailError", message(switch (outcome.result()) {
            case INVALID_EMAIL -> "signup.error.email.invalid";
            case UNCHANGED -> "account.emailChange.email.unchanged";
            default -> "account.emailRequired.email.unavailable";
        }));
        addCorrectionReference(model, correction);
        return CHANGE_VIEW;
    }

    /** 현재 이메일은 가려서만 보여준다. 대상 id 는 화면으로 내보내지 않는다. */
    private void addCorrectionReference(Model model, PendingEmailCorrection correction) {
        model.addAttribute("maskedCurrentEmail",
                EmailPolicy.mask(correction.expectedCurrentEmail()));
        model.addAttribute("pageTitle", message("account.emailChange.pageTitle"));
        if (!model.containsAttribute("enteredEmail")) {
            model.addAttribute("enteredEmail", "");
        }
    }

    private PendingEmailCorrection authorizedCorrection(HttpSession session) {
        Object value = session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE);
        return value instanceof PendingEmailCorrection correction
                && correction.isAuthorizedAt(Instant.now()) ? correction : null;
    }


    private boolean isTarget(CustomUserDetails userDetails) {
        return userDetails != null
                && missingEmailRegistrationService.requiresEmailRegistration(userDetails.getId());
    }

    /** 세션과 그 안의 인증 대기 문맥은 남기고 인증만 비운다. */
    private void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        securityContextRepository.saveContext(emptyContext, request, response);
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }
}
