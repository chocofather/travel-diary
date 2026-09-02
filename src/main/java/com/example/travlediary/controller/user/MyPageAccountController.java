package com.example.travlediary.controller.user;

import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.AccountEditForm;
import com.example.travlediary.dto.AccountVerifyForm;
import com.example.travlediary.dto.AccountWithdrawalForm;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountReauthenticationService;
import com.example.travlediary.service.user.AccountValidationException;
import com.example.travlediary.service.user.MyPageAccountService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialWithdrawalException;
import com.example.travlediary.service.user.SocialWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage/account")
public class MyPageAccountController {

    private final MyPageAccountService accountService;
    private final AccountReauthenticationService reauthenticationService;
    private final SocialAccountService socialAccountService;
    private final SocialWithdrawalService socialWithdrawalService;

    @GetMapping
    public String verifyForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                             HttpSession session,
                             Model model) {
        // 확인 화면을 떠나 계정 관리로 돌아온 경우 탈퇴 intent를 재사용하지 않는다.
        session.removeAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            model.addAttribute("socialAccounts",
                    socialAccountService.findAllByUserId(userDetails.getId()));
            model.addAttribute("pageTitle", "계정 관리 | 마이페이지");
            return "mypage/account-social";
        }
        if (reauthenticationService.isVerified(session, userDetails.getId())) {
            return "redirect:/mypage/account/edit";
        }
        if (!model.containsAttribute("verifyForm")) {
            model.addAttribute("verifyForm", new AccountVerifyForm());
        }
        model.addAttribute("pageTitle", "본인 확인 | 마이페이지");
        return "mypage/account-verify";
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
            redirectAttributes.addFlashAttribute(
                    "socialWithdrawalError", exception.getMessage());
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
            redirectAttributes.addFlashAttribute(
                    "socialWithdrawalError", "본인 확인 정보가 만료되었습니다. 다시 시도해주세요.");
            return "redirect:/mypage/account";
        }

        model.addAttribute("providerName", providerName(pending.provider()));
        model.addAttribute("providerAuthorizationUrl",
                "/oauth2/authorization/" + pending.provider().name().toLowerCase());
        model.addAttribute("pageTitle", "회원 탈퇴 | 마이페이지");
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
            bindingResult.rejectValue(
                    "currentPassword", "required", "현재 비밀번호를 입력해주세요.");
        } else if (!accountService.verifyCurrentPassword(
                userDetails.getId(), form.getCurrentPassword())) {
            bindingResult.rejectValue(
                    "currentPassword", "mismatch", "비밀번호가 일치하지 않습니다.");
        }

        if (bindingResult.hasErrors()) {
            form.setCurrentPassword(null);
            model.addAttribute("pageTitle", "본인 확인 | 마이페이지");
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
        prepareEditModel(model, details, accountEditForm(details));
        return "mypage/account-edit";
    }

    @PostMapping("/edit")
    public String updateAccount(
            @ModelAttribute("accountForm") AccountEditForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!accountService.hasLocalPassword(userDetails.getId())) {
            return "redirect:/mypage/account";
        }
        if (!requireVerification(session, userDetails.getId(), redirectAttributes)) {
            return "redirect:/mypage/account";
        }
        try {
            accountService.updateAccountDetails(userDetails.getId(), form);
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            prepareEditModel(
                    model, accountService.getAccountDetails(userDetails.getId()), form);
            return "mypage/account-edit";
        }

        redirectAttributes.addFlashAttribute(
                "accountMessage", "회원정보가 수정되었습니다.");
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
            prepareEditModel(model, accountService.getAccountDetails(userDetails.getId()), null);
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
            accountService.withdraw(userDetails.getId(), form.getCurrentPassword());
        } catch (AccountValidationException exception) {
            reject(bindingResult, exception);
        }

        if (bindingResult.hasErrors()) {
            form.setCurrentPassword(null);
            prepareEditModel(model, accountService.getAccountDetails(userDetails.getId()), null);
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
                    "verificationMessage", "본인 확인이 필요합니다.");
        }
        return verified;
    }

    private void prepareEditModel(Model model,
                                  AccountDetailsDto details,
                                  AccountEditForm accountForm) {
        model.addAttribute("account", details);
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm",
                    accountForm == null ? accountEditForm(details) : accountForm);
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
        }
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new AccountWithdrawalForm());
        }
        model.addAttribute("pageTitle", "회원정보 수정 | 마이페이지");
    }

    private AccountEditForm accountEditForm(AccountDetailsDto details) {
        AccountEditForm form = new AccountEditForm();
        form.setFullName(details.getFullName());
        form.setUserPhone(details.getUserPhone());
        form.setUserBirth(details.getUserBirth());
        return form;
    }

    private void reject(BindingResult bindingResult,
                        AccountValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("account", exception.getMessage());
        } else {
            bindingResult.rejectValue(
                    exception.getField(), "account", exception.getMessage());
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

    private String providerName(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
        };
    }
}
