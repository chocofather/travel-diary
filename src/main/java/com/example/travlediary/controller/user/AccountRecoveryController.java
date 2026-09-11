package com.example.travlediary.controller.user;

import com.example.travlediary.service.user.AccountRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 메일로 받은 계정 복구 링크 처리.
 *
 * <p>복구 요청(발송)은 탈퇴 유예 안내 화면에서만 하고, 여기는 링크 확인과 확정만 맡는다.
 * 링크는 로그인하지 않은 브라우저에서도 열리므로 공개 경로다.
 */
@Controller
@RequestMapping("/users/recover-account")
public class AccountRecoveryController {

    static final String INVALID_LINK_REDIRECT = "redirect:/login?recoveryLinkInvalid=true";

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryController.class);

    private final AccountRecoveryService accountRecoveryService;

    public AccountRecoveryController(AccountRecoveryService accountRecoveryService) {
        this.accountRecoveryService = accountRecoveryService;
    }

    /**
     * 메일 링크 진입. 메일 보안 스캐너나 링크 미리보기가 대신 열어 볼 수 있는 요청이므로
     * 여기서는 회원 상태도 토큰도 바꾸지 않고 확인 화면만 보여준다.
     */
    @GetMapping("/confirm")
    public String showConfirmation(@RequestParam(required = false) String token, Model model) {
        final boolean usable;
        try {
            usable = accountRecoveryService.isRecoveryLinkUsable(token);
        } catch (RuntimeException exception) {
            log.error("Account recovery link check failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            return INVALID_LINK_REDIRECT;
        }
        if (!usable) {
            return INVALID_LINK_REDIRECT;
        }
        // 원문 토큰은 확인 폼의 hidden 으로만 이어진다. 세션에도 로그에도 남기지 않는다.
        model.addAttribute("recoveryToken", token);
        return "recover-account-confirm";
    }

    /**
     * 사용자가 확인 화면에서 직접 누른 복구. 여기서만 상태가 바뀐다.
     * 복구에 성공하면 탈퇴 유예 상태로 인증된 세션이 남아 있을 수 있으므로 세션을 정리하고
     * 다시 로그인하게 한다. 자동 로그인은 하지 않는다.
     */
    @PostMapping("/confirm")
    public String confirmRecovery(@RequestParam(required = false) String token,
                                  Authentication authentication,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        final boolean recovered;
        try {
            recovered = accountRecoveryService.confirmRecovery(token);
        } catch (RuntimeException exception) {
            log.error("Account recovery confirmation failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            return INVALID_LINK_REDIRECT;
        }
        if (!recovered) {
            return INVALID_LINK_REDIRECT;
        }

        clearSession(request, response, authentication);
        return "redirect:/login?accountRecovered=true";
    }

    /** 탈퇴 유예 세션을 그대로 정상 서비스 세션으로 승격시키지 않는다. */
    private void clearSession(HttpServletRequest request,
                              HttpServletResponse response,
                              Authentication authentication) {
        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(request, response, authentication);
        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);
        SecurityContextHolder.clearContext();
    }
}
