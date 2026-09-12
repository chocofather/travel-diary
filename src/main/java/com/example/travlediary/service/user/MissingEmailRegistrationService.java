package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 이메일 없이 남아 있는 예전 Kakao/Naver 회원의 이메일 등록.
 *
 * <p>새 계정을 만들지 않는다. 기존 users 행을 그대로 두고 이메일과 인증 토큰만 채워
 * 신규 가입과 같은 INACTIVE 인증 대기 상태로 되돌린다. 인증이 끝나면 같은 회원이 ACTIVE 로
 * 돌아오고 닉네임·프로필·작성 콘텐츠·social_accounts 는 처음부터 끝까지 그대로다.
 *
 * <p>토큰 발급 규칙(24시간)과 재발송 정책(60초)은 {@link EmailVerificationService} 를 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
public class MissingEmailRegistrationService {

    private static final Logger log =
            LoggerFactory.getLogger(MissingEmailRegistrationService.class);

    private final UserMapper userMapper;
    private final SocialEmailAccountResolver socialEmailAccountResolver;
    private final EmailVerificationService emailVerificationService;

    /** 이메일 등록을 마쳐야 서비스를 쓸 수 있는 회원인지. 세션이 아니라 DB 로 판정한다. */
    public boolean requiresEmailRegistration(Long userId) {
        return userId != null && userMapper.isSocialAccountMissingEmail(userId);
    }

    /** 입력 칸의 상태 확인. 다른 회원의 계정 상태를 구분해서 알려주지 않는다. */
    public EmailAvailability checkAvailability(String enteredEmail) {
        return switch (socialEmailAccountResolver.classifyEnteredEmail(enteredEmail).status()) {
            case AVAILABLE -> EmailAvailability.AVAILABLE;
            case INVALID -> EmailAvailability.INVALID;
            // 다른 계정이 쓰는 중이라는 사실만 알리고 그 계정 상태는 노출하지 않는다.
            case EXISTING_ACTIVE, UNAVAILABLE -> EmailAvailability.UNAVAILABLE;
        };
    }

    /**
     * 이메일 등록 시작. AJAX 결과와 무관하게 여기서 이메일과 대상 조건을 모두 다시 확인한다.
     *
     * <p>확인과 갱신은 조건을 그대로 담은 UPDATE 한 번으로 처리한다. 화면을 열어 둔 사이
     * 탈퇴·제재로 상태가 바뀌었으면 0행이 되어 아무것도 바뀌지 않는다.
     */
    public Outcome start(Long userId, String enteredEmail) {
        if (userId == null) {
            return Outcome.notEligible();
        }
        EmailAvailability availability = checkAvailability(enteredEmail);
        if (availability != EmailAvailability.AVAILABLE) {
            return new Outcome(availability == EmailAvailability.INVALID
                    ? Result.INVALID_EMAIL : Result.EMAIL_TAKEN, null);
        }
        String email = EmailPolicy.normalizeAndValidate(enteredEmail);

        // 토큰 발급 규칙은 신규 가입과 같은 서비스가 정한다. 여기서 만료·형식을 다시 만들지 않는다.
        User token = new User();
        emailVerificationService.initializeVerification(token);

        final int updated;
        try {
            updated = userMapper.startEmailVerificationForSocialAccount(
                    userId, email, token.getVerificationToken(),
                    token.getVerificationTokenExp(), token.getVerificationRequestedAt());
        } catch (DuplicateKeyException exception) {
            // 확인과 등록 사이에 다른 회원이 같은 이메일을 차지했다. 서버 오류로 보이면 안 된다.
            return new Outcome(Result.EMAIL_TAKEN, null);
        }
        if (updated != 1) {
            log.info("Missing email registration was not applicable: userId={}", userId);
            return Outcome.notEligible();
        }

        token.setId(userId);
        token.setUserEmail(email);
        boolean requested = emailVerificationService.requestInitialVerification(token);
        log.info("Missing email registration started: userId={}, mailRequested={}",
                userId, requested);
        return new Outcome(requested ? Result.STARTED : Result.STARTED_WITHOUT_MAIL, email);
    }

    public enum EmailAvailability {
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }

    public enum Result {
        /** 등록과 인증메일 발송까지 끝났다. */
        STARTED,
        /** 등록은 됐지만 메일 발송에 실패했다. 재발송 화면에서 다시 받을 수 있다. */
        STARTED_WITHOUT_MAIL,
        /** 이미 다른 회원이 쓰는 이메일이다. */
        EMAIL_TAKEN,
        /** 우리 정책을 통과하지 못하는 형식이다. */
        INVALID_EMAIL,
        /** 더는 이메일 등록 대상이 아니다. 상태가 바뀌었거나 이미 이메일이 있다. */
        NOT_ELIGIBLE;

        /** 인증 대기 화면으로 보내야 하는 결과인지. */
        public boolean started() {
            return this == STARTED || this == STARTED_WITHOUT_MAIL;
        }
    }

    /** @param userEmail 등록에 성공했을 때의 정규화된 이메일. 그 외에는 null */
    public record Outcome(Result result, String userEmail) {

        static Outcome notEligible() {
            return new Outcome(Result.NOT_ELIGIBLE, null);
        }
    }
}
