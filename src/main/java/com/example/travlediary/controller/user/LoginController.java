package com.example.travlediary.controller.user;

import com.example.travlediary.model.PendingSocialLoginLink;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Controller
public class LoginController {
    private final LoginThrottle loginThrottle;
    private final MessageSource messageSource;

    public LoginController(LoginThrottle loginThrottle, MessageSource messageSource) {
        this.loginThrottle = loginThrottle;
        this.messageSource = messageSource;
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication,
                            HttpServletRequest request,
                            Model model) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        addLoginFormState(request, model);
        addPendingSocialLink(request, model);
        return "login"; // login.html을 반환
    }

    /**
     * 기존 계정 로그인을 기다리는 소셜 연결이 있으면 안내와 함께 그 provider 버튼을 막는다.
     * 같은 provider 로 다시 로그인하면 가입 화면으로 되돌아가는 순환이 되기 때문이다.
     */
    private void addPendingSocialLink(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        if (!(session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)
                instanceof PendingSocialLoginLink pending)
                || !pending.isUsableAt(Instant.now())) {
            return;
        }
        model.addAttribute("pendingSocialLinkProvider", pending.provider().name());
        model.addAttribute("pendingSocialLinkProviderName", messageSource.getMessage(
                "mypage.account.social.provider." + pending.provider().name(),
                null, LocaleContextHolder.getLocale()));
    }

    private void addLoginFormState(HttpServletRequest request, Model model) {
        model.addAttribute("loginBlocked", false);
        HttpSession session = request.getSession(false);
        LoginFormState storedState = null;
        if (session != null) {
            Object value = session.getAttribute(LoginFormState.SESSION_ATTRIBUTE);
            if (value instanceof LoginFormState state) {
                storedState = state;
            }
        }

        String username = storedState == null ? null : storedState.username();
        LoginThrottleStatus currentStatus = storedState == null
                ? loginThrottle.ipStatus(request.getRemoteAddr())
                : loginThrottle.status(username, request.getRemoteAddr());
        LoginFormState currentState = LoginFormState.from(username, currentStatus);

        if (!currentStatus.blocked()) {
            if (storedState != null) {
                model.addAttribute("loginFormState", currentState);
                session.removeAttribute(LoginFormState.SESSION_ATTRIBUTE);
            }
            return;
        }

        long remainingMillis = Duration.between(
                Instant.now(), currentStatus.blockedUntil()).toMillis();
        if (remainingMillis <= 0) {
            if (session != null) {
                session.removeAttribute(LoginFormState.SESSION_ATTRIBUTE);
            }
            return;
        }

        long remainingSeconds = (remainingMillis + 999) / 1_000;
        if (storedState != null) {
            session.setAttribute(LoginFormState.SESSION_ATTRIBUTE, currentState);
        }
        model.addAttribute("loginFormState", currentState);
        model.addAttribute("loginBlocked", true);
        model.addAttribute("loginRemainingSeconds", remainingSeconds);
        model.addAttribute("loginRemainingText", formatRemaining(remainingSeconds));
    }

    /** 남은 시간 문구는 언어별 어순을 위해 message parameter 로 조립한다. */
    private String formatRemaining(long seconds) {
        Locale locale = LocaleContextHolder.getLocale();
        if (seconds < 60) {
            return message("login.throttle.remaining.seconds", locale, seconds);
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return remainingSeconds == 0
                ? message("login.throttle.remaining.minutes", locale, minutes)
                : message("login.throttle.remaining.minutesSeconds", locale,
                        minutes, remainingSeconds);
    }

    private String message(String code, Locale locale, Object... arguments) {
        return messageSource.getMessage(code, arguments, locale);
    }
}
