package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingEmailCorrection;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 이메일 인증 대기 중 오타로 잘못 저장된 주소를 고치는 절차.
 *
 * <p>가입 경로를 가리지 않는다. 일반 회원가입·신규 Kakao/Naver 가입·예전 소셜 회원 이메일 보완
 * 모두 같은 자격 조건(이메일 인증 대기)으로 들어오고, 본인확인 방법만 다르다.
 * 비밀번호가 있는 계정은 그 비밀번호로, 소셜 계정은 연결된 provider 재인증으로 확인한다.
 *
 * <p>본인확인 성공은 로그인이 아니라 "이메일을 바꿔도 되는 본인" 이라는 권한일 뿐이다.
 * 토큰 발급 규칙(24시간)과 재발송 정책(60초)은 {@link EmailVerificationService} 를 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
public class EmailCorrectionService {

    /** 재인증부터 새 이메일 저장까지 주어지는 시간. */
    static final Duration CORRECTION_TTL = Duration.ofMinutes(10);

    private static final Logger log =
            LoggerFactory.getLogger(EmailCorrectionService.class);

    private final UserMapper userMapper;
    private final SocialAccountService socialAccountService;
    private final SocialEmailAccountResolver socialEmailAccountResolver;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 지금 이메일 변경을 시작할 수 있는 회원인지.
     * 가입 경로를 가리지 않고 "이메일 인증 대기" 이기만 하면 된다.
     */
    public boolean isCorrectable(Long userId) {
        return userId != null && userMapper.isEmailVerificationPending(userId);
    }

    /**
     * 지금 대기 중인 이메일로 쓸 수 있는 본인확인 수단.
     *
     * <p>세션에 표시를 남기지 않고 DB 만 본다. 브라우저를 닫았다 다시 로그인해도 같은 답이 나온다.
     * 후보가 없으면 그 계정은 이 화면에서 이메일을 고칠 수 없다.
     */
    public CorrectionOptions optionsFor(String pendingEmail) {
        User target = pendingTarget(pendingEmail);
        if (target == null) {
            return CorrectionOptions.none();
        }
        return new CorrectionOptions(
                target.getUserPassword() != null && !target.getUserPassword().isBlank(),
                reauthenticationProviders(target.getId()));
    }

    /** 본인확인에 쓸 수 있는 provider. 그 계정에 실제로 연결된 Kakao/Naver 만 후보다. */
    public List<SocialProvider> reauthenticationProviders(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return socialAccountService.findAllByUserId(userId).stream()
                .map(SocialAccount::getProvider)
                .filter(provider -> provider == SocialProvider.KAKAO
                        || provider == SocialProvider.NAVER)
                .distinct()
                .toList();
    }

    /**
     * 소셜 재인증으로 이메일 변경 본인확인 시작. 아직 아무 권한도 주지 않는다.
     * 대상과 provider 를 서버가 확정해 문맥으로만 남기고, 승인은 OAuth 왕복 뒤에 붙는다.
     */
    public PendingEmailCorrection beginSocial(String pendingEmail, SocialProvider provider) {
        User target = pendingTarget(pendingEmail);
        if (target == null
                || (provider != SocialProvider.KAKAO && provider != SocialProvider.NAVER)
                || !reauthenticationProviders(target.getId()).contains(provider)) {
            return null;
        }
        return newCorrection(target, PendingEmailCorrection.Method.SOCIAL, provider);
    }

    /**
     * 비밀번호 확인으로 이메일 변경 본인확인 시작. 비밀번호가 없는 소셜 전용 계정은 쓸 수 없다.
     * 여기서도 아직 권한은 없다. 비밀번호가 맞아야 {@link #authorizeLocal} 이 권한을 붙인다.
     */
    public PendingEmailCorrection beginLocal(String pendingEmail) {
        User target = pendingTarget(pendingEmail);
        if (target == null
                || target.getUserPassword() == null || target.getUserPassword().isBlank()) {
            return null;
        }
        return newCorrection(target, PendingEmailCorrection.Method.LOCAL, null);
    }

    /**
     * 비밀번호 재확인. 맞을 때만 변경 권한을 붙인다.
     *
     * <p>로그인이 아니다. 인증을 만들지 않고 이 문맥에만 권한 표시를 남긴다.
     * 대상 계정은 여전히 이메일 인증 대기라 서비스에 들어갈 수 없다.
     */
    public PendingEmailCorrection authorizeLocal(PendingEmailCorrection pending,
                                                 String rawPassword) {
        if (pending == null || !pending.isValidAt(Instant.now())
                || pending.method() != PendingEmailCorrection.Method.LOCAL
                || rawPassword == null || rawPassword.isEmpty()
                || !isCorrectable(pending.targetUserId())) {
            return null;
        }
        User target = userMapper.findById(pending.targetUserId());
        if (target == null || target.getUserPassword() == null
                || !passwordEncoder.matches(rawPassword, target.getUserPassword())) {
            log.info("Email correction password check failed: userId={}",
                    pending.targetUserId());
            return null;
        }
        return pending.authorize();
    }

    /** 지금 대기 중인 이메일의 주인. 이메일 인증 대기가 아니면 null 이다. */
    private User pendingTarget(String pendingEmail) {
        final String email;
        try {
            email = EmailPolicy.normalizeAndValidate(pendingEmail);
        } catch (RegistrationValidationException exception) {
            return null;
        }
        User target = userMapper.findByEmail(email);
        return target != null && target.getId() != null && isCorrectable(target.getId())
                ? target : null;
    }

    private PendingEmailCorrection newCorrection(User target,
                                                 PendingEmailCorrection.Method method,
                                                 SocialProvider provider) {
        Instant createdAt = Instant.now();
        return new PendingEmailCorrection(
                UUID.randomUUID().toString(), target.getId(), target.getUserEmail(),
                method, provider, false, createdAt, createdAt.plus(CORRECTION_TTL));
    }

    /** 화면이 보여줄 본인확인 수단. 둘 다 없으면 이 계정은 대상이 아니다. */
    public record CorrectionOptions(boolean passwordAvailable, List<SocialProvider> providers) {

        static CorrectionOptions none() {
            return new CorrectionOptions(false, List.of());
        }

        public boolean available() {
            return passwordAvailable || !providers.isEmpty();
        }
    }

    /**
     * OAuth 로 확인된 신원이 정말 그 계정의 것인지 본다.
     *
     * <p>"OAuth 에 성공했다" 만으로는 통과하지 못한다. (provider, provider_user_id) 가
     * target 회원의 social_accounts 와 정확히 같아야 한다. 남의 Kakao/Naver 로는 통과할 수 없다.
     */
    public PendingEmailCorrection authorize(PendingEmailCorrection pending,
                                            SocialProvider provider,
                                            String providerUserId) {
        if (pending == null || !pending.isValidAt(Instant.now())
                || pending.method() != PendingEmailCorrection.Method.SOCIAL
                || pending.provider() != provider
                || providerUserId == null || providerUserId.isBlank()) {
            return null;
        }
        SocialAccount owner =
                socialAccountService.findByProviderAndProviderUserId(provider, providerUserId);
        if (owner == null || !pending.targetUserId().equals(owner.getUserId())) {
            log.info("Email correction reauthentication did not match the target: provider={}",
                    provider);
            return null;
        }
        if (!isCorrectable(pending.targetUserId())) {
            return null;
        }
        return pending.authorize();
    }

    /** 새 이메일 입력 칸의 상태 확인. 다른 회원의 계정 상태는 구분해서 알려주지 않는다. */
    public Availability checkAvailability(String enteredEmail) {
        return switch (socialEmailAccountResolver.classifyEnteredEmail(enteredEmail).status()) {
            case AVAILABLE -> Availability.AVAILABLE;
            case INVALID -> Availability.INVALID;
            case EXISTING_ACTIVE, UNAVAILABLE -> Availability.UNAVAILABLE;
        };
    }

    /**
     * 새 이메일로 교체. AJAX 결과와 무관하게 권한·대상 상태·이메일을 여기서 모두 다시 확인한다.
     *
     * <p>지금 이메일이 문맥의 기대값과 같을 때만 바뀌므로, 그사이 예전 링크로 인증이 끝나
     * ACTIVE 가 됐다면 아무것도 바꾸지 않는다. 성공하면 새 토큰이 예전 토큰을 덮어써
     * 예전 이메일로 갔던 링크는 그 즉시 무효가 된다.
     */
    public Outcome change(PendingEmailCorrection pending, String newEmail) {
        if (pending == null || !pending.isAuthorizedAt(Instant.now())) {
            return Outcome.notAuthorized();
        }
        Availability availability = checkAvailability(newEmail);
        if (availability != Availability.AVAILABLE) {
            return new Outcome(availability == Availability.INVALID
                    ? Result.INVALID_EMAIL : Result.EMAIL_TAKEN, null);
        }
        String email = EmailPolicy.normalizeAndValidate(newEmail);
        if (email.equals(pending.expectedCurrentEmail())) {
            // 같은 주소로는 바꿀 것이 없다. 기존 토큰을 헛되이 무효화하지 않는다.
            return new Outcome(Result.UNCHANGED, email);
        }

        User token = new User();
        emailVerificationService.initializeVerification(token);

        final int updated;
        try {
            updated = userMapper.changePendingVerificationEmail(
                    pending.targetUserId(), email, pending.expectedCurrentEmail(),
                    token.getVerificationToken(), token.getVerificationTokenExp(),
                    token.getVerificationRequestedAt());
        } catch (DuplicateKeyException exception) {
            return new Outcome(Result.EMAIL_TAKEN, null);
        }
        if (updated != 1) {
            log.info("Email correction was no longer applicable: userId={}",
                    pending.targetUserId());
            return Outcome.notAuthorized();
        }

        token.setId(pending.targetUserId());
        token.setUserEmail(email);
        // 본인 재인증까지 거친 명시적 변경이라 첫 메일은 cooldown 없이 바로 보낸다.
        boolean requested = emailVerificationService.requestInitialVerification(token);
        log.info("Email correction completed: userId={}, mailRequested={}",
                pending.targetUserId(), requested);
        return new Outcome(requested ? Result.CHANGED : Result.CHANGED_WITHOUT_MAIL, email);
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }

    public enum Result {
        CHANGED,
        /** 바뀌었지만 메일 발송에 실패했다. 재발송 화면에서 다시 받을 수 있다. */
        CHANGED_WITHOUT_MAIL,
        /** 지금 인증 대기 중인 주소와 같다. 바꿀 것이 없다. */
        UNCHANGED,
        EMAIL_TAKEN,
        INVALID_EMAIL,
        /** 권한이 없거나 만료됐다. 대상 상태가 바뀌었을 수도 있다. */
        NOT_AUTHORIZED;

        public boolean changed() {
            return this == CHANGED || this == CHANGED_WITHOUT_MAIL;
        }
    }

    /** @param userEmail 변경에 성공했을 때의 새 이메일. 실패하면 null */
    public record Outcome(Result result, String userEmail) {

        static Outcome notAuthorized() {
            return new Outcome(Result.NOT_AUTHORIZED, null);
        }
    }
}
