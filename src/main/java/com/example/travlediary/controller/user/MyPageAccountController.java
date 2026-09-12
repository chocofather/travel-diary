package com.example.travlediary.controller.user;

import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.AccountVerifyForm;
import com.example.travlediary.dto.AccountWithdrawalForm;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.PendingSocialConnection;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialConnectionNotice;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.AccountValidationException;
import com.example.travlediary.service.user.MyPageAccountService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialDisconnectionResult;
import com.example.travlediary.service.user.SocialWithdrawalException;
import com.example.travlediary.service.user.SocialWithdrawalService;
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

    @GetMapping
    public String verifyForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                             HttpSession session,
                             Model model) {
        // 확인 화면을 떠나 계정 관리로 돌아온 경우 탈퇴 intent를 재사용하지 않는다.
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            prepareSocialConnections(model, session, userDetails.getId());
            model.addAttribute("pageTitle", message("mypage.account.social.pageTitle"));
            return "mypage/account-social";
        }
        if (reauthenticationService.isVerified(session, userDetails.getId())) {
            return "redirect:/mypage/account/edit";
        }
        // 본인 확인 화면에서도 로그인 직후 처리된 소셜 연결 결과를 흘리지 않고 보여준다.
        consumeSocialConnectionNotice(model, session);
        if (!model.containsAttribute("verifyForm")) {
            model.addAttribute("verifyForm", new AccountVerifyForm());
        }
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
        if (accountService.hasLocalPassword(userDetails.getId())
                && !reauthenticationService.isVerified(session, userDetails.getId())) {
            redirectAttributes.addFlashAttribute(
                    "verificationMessage", message("mypage.account.verify.required"));
            return "redirect:/mypage/account";
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
            redirectAttributes.addFlashAttribute(
                    "verificationMessage", message("mypage.account.verify.required"));
            return "redirect:/mypage/account";
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            Model model) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
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
            model.addAttribute("pageTitle", message("mypage.account.verify.pageTitle"));
            return "mypage/account-verify";
        }

        reauthenticationService.markVerified(session, userDetails.getId());
        return "redirect:/mypage/account/edit";
    }

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!requireVerification(session, userDetails.getId(), redirectAttributes)) {
            return "redirect:/mypage/account";
        }
        AccountDetailsDto details = accountService.getAccountDetails(userDetails.getId());
        prepareEditModel(model, details, userDetails.getId(), session);
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
        if (!requireVerification(session, userDetails.getId(), redirectAttributes)) {
            return "redirect:/mypage/account";
        }
        return "redirect:/mypage/account/edit";
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
        if (!requireVerification(session, userDetails.getId(), redirectAttributes)) {
            return "redirect:/mypage/account";
        }
        try {
            accountService.changePassword(userDetails.getId(), form);
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            form.setNewPassword(null);
            form.setNewPasswordConfirm(null);
            prepareEditModel(model, accountService.getAccountDetails(userDetails.getId()),
                    userDetails.getId(), session);
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
        if (!requireVerification(session, userDetails.getId(), redirectAttributes)) {
            return "redirect:/mypage/account";
        }
        try {
            accountService.withdraw(userDetails.getId(), form.getConfirmationPhrase());
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            form.setConfirmationPhrase(null);
            prepareEditModel(model, accountService.getAccountDetails(userDetails.getId()),
                    userDetails.getId(), session);
            return "mypage/account-edit";
        }

        reauthenticationService.clear(session);
        logout(request, response, authentication);
        return "redirect:/?withdrawn=true";
    }

    private boolean requireVerification(HttpSession session,
                                        Long userId,
                                        RedirectAttributes redirectAttributes) {
        boolean verified = reauthenticationService.isVerified(session, userId);
        if (!verified) {
            redirectAttributes.addFlashAttribute(
                    "verificationMessage", message("mypage.account.verify.required"));
        }
        return verified;
    }

    private void prepareEditModel(Model model,
                                  AccountDetailsDto details,
                                  Long userId,
                                  HttpSession session) {
        model.addAttribute("account", details);
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
        }
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new AccountWithdrawalForm());
        }
        prepareSocialConnections(model, session, userId);
        model.addAttribute("pageTitle", message("mypage.account.edit.pageTitle"));
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
