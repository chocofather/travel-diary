package com.tripbora.controller.user;

import com.tripbora.dto.AccountVerifyForm;
import com.tripbora.dto.AccountWithdrawalForm;
import com.tripbora.dto.PasswordChangeForm;
import com.tripbora.model.PendingSocialConnection;
import com.tripbora.model.PendingSocialSignup;
import com.tripbora.model.PendingSocialWithdrawal;
import com.tripbora.model.SocialAccount;
import com.tripbora.model.SocialConnectionNotice;
import com.tripbora.model.SocialProvider;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.user.AccountReauthenticationService;
import com.tripbora.service.user.AccountValidationException;
import com.tripbora.service.user.MyPageAccountService;
import com.tripbora.service.user.SocialAccountService;
import com.tripbora.service.user.SocialDisconnectionResult;
import com.tripbora.service.user.SocialWithdrawalException;
import com.tripbora.service.user.SocialWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/mypage/account")
public class MyPageAccountController {

    private static final Duration SOCIAL_CONNECTION_TTL = Duration.ofMinutes(10);

    private final MyPageAccountService accountService;
    private final AccountReauthenticationService reauthenticationService;
    private final SocialAccountService socialAccountService;
    private final SocialWithdrawalService socialWithdrawalService;
    private final MessageSource messageSource;

    /**
     * 비밀번호를 다시 확인한 뒤 돌아갈 곳. 주소의 next 값은 이 목록 안에서만 고른다(임의 주소로 보내지 않는다).
     */
    enum VerificationTarget {
        /** 비밀번호 변경 화면 */
        PASSWORD("/mypage/account/edit"),
        /** 회원 탈퇴 화면 */
        WITHDRAW("/mypage/account/withdraw"),
        /** 계정 관리 화면(소셜 계정 연결·해제를 이어서 한다) */
        ACCOUNT("/mypage/account");

        private final String path;

        VerificationTarget(String path) {
            this.path = path;
        }

        String path() {
            return path;
        }

        String param() {
            return name().toLowerCase();
        }

        static VerificationTarget from(String value) {
            for (VerificationTarget target : values()) {
                if (target.param().equals(value)) {
                    return target;
                }
            }
            return ACCOUNT;
        }
    }

    /**
     * 모든 회원이 같은 주소로 들어오는 계정 관리 화면.
     * 계정 정보, 로그인 및 보안(비밀번호가 있는 회원만 비밀번호 변경 + 소셜 계정 연결), 회원 탈퇴를 한 화면에 둔다.
     * 비밀번호 확인은 비밀번호 변경·탈퇴·소셜 연결 변경처럼 실제로 바꾸는 동작에서만 요구한다.
     */
    @GetMapping
    public String accountHome(@AuthenticationPrincipal CustomUserDetails userDetails,
                              HttpSession session,
                              Model model) {
        // 확인 화면을 떠나 계정 관리로 돌아온 경우 탈퇴 intent를 재사용하지 않는다.
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        Long userId = userDetails.getId();
        model.addAttribute("account", accountService.getAccountDetails(userId));
        model.addAttribute("localPasswordAccount", accountService.hasLocalPassword(userId));
        prepareSocialConnections(model, session, userId);
        model.addAttribute("pageTitle", message("mypage.account.social.pageTitle"));
        return "mypage/account";
    }

    /** 비밀번호 확인 화면. 비밀번호가 없는 소셜 회원에게는 묻지 않는다. */
    @GetMapping("/verify")
    public String verifyForm(@RequestParam(value = "next", required = false) String next,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             HttpSession session,
                             Model model) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        VerificationTarget target = VerificationTarget.from(next);
        if (reauthenticationService.isVerified(session, userDetails.getId())) {
            return "redirect:" + target.path();
        }
        if (!model.containsAttribute("verifyForm")) {
            model.addAttribute("verifyForm", new AccountVerifyForm());
        }
        model.addAttribute("verificationTarget", target.param());
        model.addAttribute("pageTitle", message("mypage.account.verify.pageTitle"));
        return "mypage/account-verify";
    }

    @PostMapping("/social-connections/{registrationId}")
    public String beginSocialConnection(
            @PathVariable String registrationId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        SocialProvider provider = SocialProvider.fromRegistrationId(registrationId)
                .orElse(null);
        if (provider == null) {
            session.setAttribute(
                    SocialConnectionNotice.SESSION_ATTRIBUTE,
                    new SocialConnectionNotice(SocialConnectionNotice.Type.ERROR, null));
            return "redirect:/mypage/account";
        }
        // 비밀번호가 있는 회원은 연결 전에 비밀번호를 한 번 더 확인한다(소셜 전용 회원은 묻지 않는다).
        if (accountService.hasLocalPassword(userDetails.getId())
                && !reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.ACCOUNT,
                    "mypage.account.verify.socialRequired", redirectAttributes);
        }

        session.removeAttribute(PendingSocialConnection.SESSION_ATTRIBUTE);
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        if (socialAccountService.findByUserIdAndProvider(
                userDetails.getId(), provider) != null) {
            session.setAttribute(
                    SocialConnectionNotice.SESSION_ATTRIBUTE,
                    new SocialConnectionNotice(
                            SocialConnectionNotice.Type.ALREADY_CONNECTED, provider));
            return "redirect:/mypage/account";
        }

        Instant now = Instant.now();
        session.setAttribute("userId", userDetails.getId());
        session.setAttribute(
                PendingSocialConnection.SESSION_ATTRIBUTE,
                new PendingSocialConnection(
                        UUID.randomUUID().toString(),
                        userDetails.getId(),
                        provider,
                        now,
                        now.plus(SOCIAL_CONNECTION_TTL),
                        null));
        return "redirect:/oauth2/authorization/" + registrationId;
    }

    @PostMapping("/social-connections/{registrationId}/disconnect")
    public String disconnectSocialConnection(
            @PathVariable String registrationId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        SocialProvider provider = SocialProvider.fromRegistrationId(registrationId)
                .orElse(null);
        if (provider == null) {
            session.setAttribute(
                    SocialConnectionNotice.SESSION_ATTRIBUTE,
                    new SocialConnectionNotice(
                            SocialConnectionNotice.Type.DISCONNECT_ERROR, null));
            return "redirect:/mypage/account";
        }
        if (accountService.hasLocalPassword(userDetails.getId())
                && !reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.ACCOUNT,
                    "mypage.account.verify.socialRequired", redirectAttributes);
        }

        SocialDisconnectionResult result;
        try {
            result = socialAccountService.disconnectFromUser(userDetails.getId(), provider);
        } catch (RuntimeException exception) {
            log.warn("Failed to disconnect {} account for user {}",
                    provider, userDetails.getId(), exception);
            session.setAttribute(
                    SocialConnectionNotice.SESSION_ATTRIBUTE,
                    new SocialConnectionNotice(
                            SocialConnectionNotice.Type.DISCONNECT_ERROR, provider));
            return "redirect:/mypage/account";
        }
        SocialConnectionNotice.Type noticeType = switch (result) {
            case DISCONNECTED -> SocialConnectionNotice.Type.DISCONNECTED;
            case LAST_LOGIN_METHOD -> SocialConnectionNotice.Type.LAST_LOGIN_METHOD;
            case ALREADY_DISCONNECTED -> SocialConnectionNotice.Type.ALREADY_DISCONNECTED;
        };
        session.setAttribute(
                SocialConnectionNotice.SESSION_ATTRIBUTE,
                new SocialConnectionNotice(noticeType, provider));
        return "redirect:/mypage/account";
    }

    @PostMapping("/social-withdrawal")
    public String beginSocialWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        try {
            PendingSocialWithdrawal pending =
                    socialWithdrawalService.begin(userDetails.getId());
            session.setAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);
            return "redirect:/mypage/account/social-withdrawal/confirm";
        } catch (SocialWithdrawalException exception) {
            redirectAttributes.addFlashAttribute("socialWithdrawalError",
                    messageOrDefault(exception.getMessageCode(), exception.getMessage()));
            return "redirect:/mypage/account";
        }
    }

    @GetMapping("/social-withdrawal/confirm")
    public String socialWithdrawalConfirmation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (accountService.hasLocalPassword(userDetails.getId())) {
            session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
            return "redirect:/mypage/account";
        }

        Object value = session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialWithdrawal pending)
                || !socialWithdrawalService.isValid(pending, userDetails.getId())) {
            session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
            redirectAttributes.addFlashAttribute("socialWithdrawalError",
                    message("mypage.account.withdrawal.social.expired"));
            return "redirect:/mypage/account";
        }

        model.addAttribute("providerName", providerName(pending.provider()));
        model.addAttribute("providerAuthorizationUrl",
                "/oauth2/authorization/" + pending.provider().name().toLowerCase());
        model.addAttribute("pageTitle", message("mypage.account.withdrawal.pageTitle"));
        return "mypage/social-withdrawal-confirm";
    }

    @PostMapping("/social-withdrawal/cancel")
    public String cancelSocialWithdrawal(HttpSession session) {
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        return "redirect:/mypage/account";
    }

    @PostMapping("/verify-password")
    public String verifyPassword(
            @ModelAttribute("verifyForm") AccountVerifyForm form,
            BindingResult bindingResult,
            @RequestParam(value = "next", required = false) String next,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        VerificationTarget target = VerificationTarget.from(next);
        if (form.getCurrentPassword() == null || form.getCurrentPassword().isEmpty()) {
            bindingResult.rejectValue("currentPassword",
                    "mypage.account.error.currentPassword.required",
                    "현재 비밀번호를 입력해주세요.");
        } else if (!accountService.verifyCurrentPassword(
                userDetails.getId(), form.getCurrentPassword())) {
            bindingResult.rejectValue("currentPassword",
                    "mypage.account.error.currentPassword.mismatch",
                    "비밀번호가 일치하지 않습니다.");
        }

        if (bindingResult.hasErrors()) {
            form.setCurrentPassword(null);
            model.addAttribute("verificationTarget", target.param());
            model.addAttribute("pageTitle", message("mypage.account.verify.pageTitle"));
            return "mypage/account-verify";
        }

        reauthenticationService.markVerified(session, userDetails.getId());
        if (target == VerificationTarget.ACCOUNT) {
            // 소셜 계정 연결·해제를 하려다 확인하러 온 경우: 계정 관리로 돌아가 이어서 누르게 한다.
            redirectAttributes.addFlashAttribute(
                    "verifiedMessage", message("mypage.account.verify.socialDone"));
        }
        return "redirect:" + target.path();
    }

    /** 비밀번호 변경 전용 화면. 최근에 비밀번호를 확인한 회원만 들어온다. */
    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.PASSWORD,
                    "mypage.account.verify.required", redirectAttributes);
        }
        preparePasswordModel(model);
        return "mypage/account-edit";
    }

    @PostMapping("/edit")
    public String readonlyAccountRedirect(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.PASSWORD,
                    "mypage.account.verify.required", redirectAttributes);
        }
        return "redirect:/mypage/account/edit";
    }

    /**
     * 비밀번호가 있는 회원의 회원 탈퇴 화면. 최근에 비밀번호를 확인한 회원만 들어오고, 확인 문구를 한 번 더 받는다.
     * 비밀번호가 없는 소셜 회원은 계정 관리 화면의 소셜 인증 탈퇴 절차를 그대로 쓴다.
     */
    @GetMapping("/withdraw")
    public String withdrawForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.WITHDRAW,
                    "mypage.account.verify.required", redirectAttributes);
        }
        prepareWithdrawalModel(model);
        return "mypage/account-withdraw";
    }

    @PostMapping("/password")
    public String changePassword(
            @ModelAttribute("passwordForm") PasswordChangeForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication authentication,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.PASSWORD,
                    "mypage.account.verify.required", redirectAttributes);
        }
        try {
            accountService.changePassword(userDetails.getId(), form);
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            form.setNewPassword(null);
            form.setNewPasswordConfirm(null);
            preparePasswordModel(model);
            return "mypage/account-edit";
        }

        reauthenticationService.clear(session);
        logout(request, response, authentication);
        return "redirect:/login?passwordChanged=true";
    }

    @PostMapping("/withdraw")
    public String withdraw(
            @ModelAttribute("withdrawalForm") AccountWithdrawalForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication authentication,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!reauthenticationService.isVerified(session, userDetails.getId())) {
            return verificationRedirect(VerificationTarget.WITHDRAW,
                    "mypage.account.verify.required", redirectAttributes);
        }
        try {
            accountService.withdraw(userDetails.getId(), form.getConfirmationPhrase());
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            form.setConfirmationPhrase(null);
            prepareWithdrawalModel(model);
            return "mypage/account-withdraw";
        }

        reauthenticationService.clear(session);
        logout(request, response, authentication);
        return "redirect:/?withdrawn=true";
    }

    /** 비밀번호를 다시 확인하러 보낸다. 확인이 끝나면 target 으로 돌아온다. */
    private String verificationRedirect(VerificationTarget target,
                                        String messageCode,
                                        RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("verificationMessage", message(messageCode));
        return "redirect:/mypage/account/verify?next=" + target.param();
    }

    private void preparePasswordModel(Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
        }
        model.addAttribute("pageTitle", message("mypage.account.password.pageTitle"));
    }

    private void prepareWithdrawalModel(Model model) {
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new AccountWithdrawalForm());
        }
        model.addAttribute("pageTitle", message("mypage.account.withdrawal.pageTitle"));
    }

    private void prepareSocialConnections(Model model,
                                          HttpSession session,
                                          Long userId) {
        var socialAccounts = socialAccountService.findAllByUserId(userId);
        EnumSet<SocialProvider> connectedProviders = EnumSet.noneOf(SocialProvider.class);
        var socialAccountsByProvider =
                new EnumMap<SocialProvider, SocialAccount>(SocialProvider.class);
        socialAccounts.forEach(account -> {
            connectedProviders.add(account.getProvider());
            socialAccountsByProvider.put(account.getProvider(), account);
        });
        model.addAttribute("socialAccounts", socialAccounts);
        model.addAttribute("socialAccountsByProvider", socialAccountsByProvider);
        model.addAttribute("socialProviders", SocialProvider.values());
        model.addAttribute("connectedSocialProviders", connectedProviders);

        consumeSocialConnectionNotice(model, session);
    }

    private void consumeSocialConnectionNotice(Model model, HttpSession session) {
        Object value = session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE);
        session.removeAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE);
        if (value instanceof SocialConnectionNotice notice) {
            model.addAttribute("socialConnectionNotice", notice);
            model.addAttribute("socialConnectionProviderName",
                    notice.provider() == null ? null : providerName(notice.provider()));
        }
    }

    /**
     * 입력 오류는 메시지 코드로 넘겨 Spring 이 요청 언어 문구를 찾게 한다.
     * 코드가 없거나 번들에 없으면 서비스가 준 한국어 문구가 그대로 쓰인다.
     */
    private void reject(BindingResult bindingResult,
                        AccountValidationException exception) {
        String code = exception.getMessageCode() == null
                ? "account"
                : exception.getMessageCode();
        if (exception.getField() == null) {
            bindingResult.reject(code, exception.getMessage());
        } else {
            bindingResult.rejectValue(exception.getField(), code, exception.getMessage());
        }
    }

    private void logout(HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) {
        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(request, response, authentication);
        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);
    }

    /** provider 는 enum 그대로 두고 표시 이름만 요청 언어로 고른다. */
    private String providerName(SocialProvider provider) {
        return message("mypage.account.social.provider." + provider.name());
    }

    /** 화면 문구는 다른 마이페이지 화면과 같은 방식으로 메시지 번들에서 가져온다. */
    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /** 코드가 없거나 번들에 없으면 서비스가 준 한국어 문구를 그대로 쓴다. */
    private String messageOrDefault(String code, String defaultMessage) {
        if (code == null) {
            return defaultMessage;
        }
        return messageSource.getMessage(
                code, null, defaultMessage, LocaleContextHolder.getLocale());
    }
}
