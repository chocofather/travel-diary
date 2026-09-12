package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingSocialLoginLink;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialConnectionNotice;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 기존 계정으로 로그인한 직후, 기다리고 있던 Kakao/Naver 연결을 마무리한다.
 *
 * <p>일반 로그인과 소셜 로그인이 같은 후처리를 쓰도록 여기 한 곳에만 둔다.
 * 실제 연결은 마이페이지와 같은 {@link SocialAccountService#connectToUser} 를 그대로 부른다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginLinkService {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginLinkService.class);

    private final UserMapper userMapper;
    private final SocialAccountService socialAccountService;

    /** 로그인해서 소유권을 확인할 때까지 기다리는 시간. */
    static final Duration LINK_TTL = Duration.ofMinutes(10);

    /**
     * "기존 계정으로 로그인하여 연결" 을 시작할 수 있는지 판정하고 문맥을 만든다.
     *
     * <p>화면이 보낸 이메일을 그대로 믿지 않고 여기서 다시 정규화·조회한다.
     * 시작할 수 없으면 null 을 돌려주고 아무것도 저장하지 않는다.
     */
    public PendingSocialLoginLink begin(PendingSocialSignup pending, String enteredEmail) {
        if (pending == null
                || (pending.provider() != SocialProvider.KAKAO
                    && pending.provider() != SocialProvider.NAVER)
                || pending.providerUserId() == null || pending.providerUserId().isBlank()) {
            return null;
        }
        // 그 사이 같은 provider 식별자가 다른 계정에 붙었으면 시작하지 않는다.
        if (socialAccountService.findByProviderAndProviderUserId(
                pending.provider(), pending.providerUserId()) != null) {
            return null;
        }

        final String normalized;
        try {
            normalized = EmailPolicy.normalizeAndValidate(enteredEmail);
        } catch (RegistrationValidationException exception) {
            return null;
        }
        User target = userMapper.findByEmail(normalized);
        if (target == null
                || target.getId() == null
                || target.getStatus() != UserStatus.ACTIVE
                || target.getDeletedAt() != null
                || !normalized.equals(target.getUserEmail())) {
            return null;
        }

        Instant createdAt = Instant.now();
        return new PendingSocialLoginLink(
                UUID.randomUUID().toString(),
                pending.provider(),
                pending.providerUserId(),
                pending.providerEmail(),
                pending.providerEmailVerified(),
                target.getId(),
                normalized,
                createdAt,
                createdAt.plus(LINK_TTL));
    }

    /**
     * 세션에 기다리는 연결이 있으면 지금 로그인한 회원에게 붙인다.
     *
     * <p>어떤 결과든 문맥은 지운다. 다시 시도하려면 소셜 로그인부터 새로 해야 한다.
     * 로그인 자체는 이미 끝난 뒤라 여기서 인증을 만들거나 되돌리지 않는다.
     *
     * @return 후처리 결과. {@link Outcome#NONE} 이면 기다리던 연결이 없었다는 뜻이고,
     *         호출하는 쪽은 평소 로그인 흐름을 그대로 이어가면 된다.
     */
    public Outcome completeAfterLogin(HttpSession session, Long authenticatedUserId) {
        if (session == null) {
            return Outcome.NONE;
        }
        Object value = session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialLoginLink pending)) {
            return Outcome.NONE;
        }
        session.removeAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE);

        if (!pending.isUsableAt(Instant.now())) {
            return notice(session, Outcome.EXPIRED, pending);
        }
        // 연결 대상이 아닌 계정으로 로그인했다. 로그인은 그대로 두고 연결만 하지 않는다.
        if (authenticatedUserId == null || !authenticatedUserId.equals(pending.targetUserId())) {
            log.info("Pending social link skipped for a different account: provider={}",
                    pending.provider());
            return notice(session, Outcome.TARGET_MISMATCH, pending);
        }
        // 화면을 거치는 동안 대상 계정이 바뀌었을 수 있다. 지금 상태를 다시 본다.
        if (!isStillLinkable(pending)) {
            return notice(session, Outcome.TARGET_UNAVAILABLE, pending);
        }

        final SocialConnectionResult result;
        try {
            result = socialAccountService.connectToUser(
                    pending.targetUserId(),
                    pending.provider(),
                    pending.providerUserId(),
                    pending.providerEmail(),
                    pending.providerEmailVerified());
        } catch (RuntimeException exception) {
            log.error("Pending social link could not be persisted: provider={}, exceptionType={}",
                    pending.provider(), exception.getClass().getSimpleName());
            return notice(session, Outcome.FAILED, pending);
        }

        return switch (result) {
            case CONNECTED -> notice(session, Outcome.CONNECTED, pending);
            case ALREADY_CONNECTED -> notice(session, Outcome.ALREADY_CONNECTED, pending);
            case OWNED_BY_ANOTHER_USER -> notice(session, Outcome.OWNED_BY_ANOTHER_USER, pending);
        };
    }

    /**
     * 탈퇴 유예·제재·휴면으로 바뀐 계정에는 붙이지 않는다. 이메일이 그대로인지도 확인해
     * 문맥을 만든 뒤 대상이 바뀌는 경합을 막는다.
     */
    private boolean isStillLinkable(PendingSocialLoginLink pending) {
        User target = userMapper.findById(pending.targetUserId());
        return target != null
                && target.getId() != null
                && target.getUserRole() != null
                && target.getStatus() == UserStatus.ACTIVE
                && target.getDeletedAt() == null
                && pending.normalizedTargetEmail().equals(target.getUserEmail());
    }

    /** 결과는 마이페이지 소셜 연결과 같은 알림 자리에 남긴다. */
    private Outcome notice(HttpSession session, Outcome outcome, PendingSocialLoginLink pending) {
        session.setAttribute(
                SocialConnectionNotice.SESSION_ATTRIBUTE,
                new SocialConnectionNotice(outcome.noticeType(), pending.provider()));
        return outcome;
    }

    public enum Outcome {
        /** 기다리던 연결이 없었다. 평소 로그인 흐름 그대로 진행한다. */
        NONE(null),
        CONNECTED(SocialConnectionNotice.Type.CONNECTED),
        ALREADY_CONNECTED(SocialConnectionNotice.Type.ALREADY_CONNECTED),
        /** 연결 대상과 다른 계정으로 로그인했다. */
        TARGET_MISMATCH(SocialConnectionNotice.Type.TARGET_MISMATCH),
        /** 대상 계정이 더는 연결할 수 있는 상태가 아니다. */
        TARGET_UNAVAILABLE(SocialConnectionNotice.Type.ERROR),
        /** 그 provider 가 이미 다른 계정에 붙어 있다. */
        OWNED_BY_ANOTHER_USER(SocialConnectionNotice.Type.ERROR),
        EXPIRED(SocialConnectionNotice.Type.ERROR),
        FAILED(SocialConnectionNotice.Type.ERROR);

        private final SocialConnectionNotice.Type noticeType;

        Outcome(SocialConnectionNotice.Type noticeType) {
            this.noticeType = noticeType;
        }

        SocialConnectionNotice.Type noticeType() {
            return noticeType;
        }

        /** 연결 결과 화면으로 보내야 하는지. NONE 일 때만 평소 이동 규칙을 그대로 쓴다. */
        public boolean handled() {
            return this != NONE;
        }
    }
}
