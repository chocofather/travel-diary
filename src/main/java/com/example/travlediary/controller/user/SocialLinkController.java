package com.example.travlediary.controller.user;

import com.example.travlediary.model.PendingSocialLink;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.EmailPolicy;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialConnectionResult;
import com.example.travlediary.service.user.SocialSignupAuthenticationException;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.Instant;

/**
 * provider 가 인증한 이메일이 기존 회원의 이메일과 같을 때 보여주는 연결 확인 화면.
 *
 * <p>자동으로 붙이지 않고 사용자가 명시적으로 확인해야 연결한다. 실제 연결은 마이페이지 연결과
 * 같은 {@link SocialAccountService#connectToUser} 를 그대로 쓴다.
 */
@Controller
@RequiredArgsConstructor
public class SocialLinkController {

    private static final String EXPIRED_REDIRECT =
            "redirect:/login?socialSignupExpired=true";
    private static final String CANCELLED_REDIRECT =
            "redirect:/login?socialLinkCancelled=true";
    private static final String FAILED_REDIRECT =
            "redirect:/login?socialLinkError=true";

    private final SocialAccountService socialAccountService;
    private final SocialSignupAuthenticationService authenticationService;
    private final UserMapper userMapper;
    private final MessageSource messageSource;

    @GetMapping("/social-link")
    public String linkPage(Authentication authentication,
                           HttpSession session,
                           Model model) {
        if (isTravelDiaryMember(authentication)) {
            return "redirect:/";
        }

        PendingSocialLink pending = validPending(session);
        if (pending == null) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        }

        addReferenceInformation(model, pending);
        return "social-link";
    }

    @PostMapping("/social-link")
    public String confirmLink(@RequestParam(name = "flowId", required = false) String flowId,
                              Authentication authentication,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        if (isTravelDiaryMember(authentication)) {
            return "redirect:/";
        }

        HttpSession session = request.getSession();
        PendingSocialLink pending = validPending(session);
        if (pending == null || !pending.matchesFlow(flowId)) {
            clearPending(session);
            return EXPIRED_REDIRECT;
        }

        // 확인 화면을 열어 둔 사이 대상 계정이 바뀌었을 수 있다. 지금 상태를 다시 본다.
        if (!isStillLinkable(pending)) {
            clearPending(session);
            return FAILED_REDIRECT;
        }

        final SocialConnectionResult result;
        try {
            result = socialAccountService.connectToUser(
                    pending.targetUserId(),
                    pending.provider(),
                    pending.providerUserId(),
                    pending.email(),
                    Boolean.TRUE);
        } catch (RuntimeException exception) {
            clearPending(session);
            return FAILED_REDIRECT;
        }
        clearPending(session);
        if (result == SocialConnectionResult.OWNED_BY_ANOTHER_USER) {
            return FAILED_REDIRECT;
        }

        try {
            authenticationService.authenticateExistingMember(
                    pending.targetUserId(), request, response);
            return null;
        } catch (SocialSignupAuthenticationException | DataAccessException exception) {
            return "redirect:/login?socialSignupError=true";
        }
    }

    @PostMapping("/social-link/cancel")
    public String cancelLink(HttpSession session) {
        clearPending(session);
        return CANCELLED_REDIRECT;
    }

    /**
     * 연결 대상은 여전히 그 이메일을 가진, 로그인 가능한 회원이어야 한다.
     * 탈퇴·파기·이메일 변경으로 조건이 깨졌으면 연결하지 않는다.
     */
    private boolean isStillLinkable(PendingSocialLink pending) {
        User target = userMapper.findById(pending.targetUserId());
        return target != null
                && target.getId() != null
                && target.getUserRole() != null
                && (target.getStatus() == UserStatus.ACTIVE
                    || target.getStatus() == UserStatus.RESTRICTED)
                && pending.email() != null
                && pending.email().equals(target.getUserEmail());
    }

    /** 계정 열거에 쓰이지 않도록 provider 이름과 가려진 이메일만 내려준다. */
    private void addReferenceInformation(Model model, PendingSocialLink pending) {
        model.addAttribute("flowId", pending.flowId());
        model.addAttribute("provider", pending.provider());
        model.addAttribute("providerDisplayName", providerDisplayName(pending.provider()));
        model.addAttribute("maskedEmail", EmailPolicy.mask(pending.email()));
    }

    /** 브랜드명은 번역하지 않고 마이페이지와 같은 provider key 를 그대로 재사용한다. */
    private String providerDisplayName(SocialProvider provider) {
        return messageSource.getMessage(
                "mypage.account.social.provider." + provider.name(),
                null, LocaleContextHolder.getLocale());
    }

    private PendingSocialLink validPending(HttpSession session) {
        Object value = session.getAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialLink pending)
                || pending.provider() != SocialProvider.GOOGLE
                || isBlank(pending.flowId())
                || isBlank(pending.providerUserId())
                || isBlank(pending.email())
                || pending.targetUserId() == null
                || pending.createdAt() == null
                || pending.isExpired(Instant.now())) {
            return null;
        }
        return pending;
    }

    private boolean isTravelDiaryMember(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails;
    }

    private void clearPending(HttpSession session) {
        session.removeAttribute(PendingSocialLink.SESSION_ATTRIBUTE);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
