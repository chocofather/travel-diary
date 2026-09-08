package com.example.travlediary.controller.user;

import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.Instant;

@Controller
public class LoginController {
    private final LoginThrottle loginThrottle;

    public LoginController(LoginThrottle loginThrottle) {
        this.loginThrottle = loginThrottle;
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
        return "login"; // login.html을 반환
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

    private String formatRemaining(long seconds) {
        if (seconds < 60) {
            return seconds + "초";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return remainingSeconds == 0
                ? minutes + "분"
                : minutes + "분 " + remainingSeconds + "초";
    }
}
