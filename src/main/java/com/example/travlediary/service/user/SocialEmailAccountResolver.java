package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 아직 연결된 적 없는 social identity 를 어떻게 처리할지 정한다.
 *
 * <p>Google 은 OIDC 가 email_verified 를 함께 주므로 그 이메일을 Travel Diary 의 공식 이메일로
 * 신뢰한다. 같은 이메일의 users 가 이미 있으면 새 users 를 만들지 않고 기존 계정 연결 확인으로 보낸다.
 * Kakao/Naver 는 아직 이메일 확보 수단이 없어 기존 신규가입 흐름을 그대로 쓴다.
 *
 * <p>탈퇴 유예 경계 판정과 만료 계정 파기는 {@link WithdrawalGraceService} 가 전담한다.
 * 여기서 purge_scheduled_at 을 직접 비교하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SocialEmailAccountResolver {

    private final UserMapper userMapper;
    private final WithdrawalGraceService withdrawalGraceService;

    public Resolution resolve(SocialProvider provider,
                              String providerEmail,
                              Boolean providerEmailVerified) {
        if (provider != SocialProvider.GOOGLE) {
            return Resolution.of(Type.NOT_APPLICABLE, null, null);
        }
        if (providerEmail == null || providerEmail.isBlank()
                || !Boolean.TRUE.equals(providerEmailVerified)) {
            return Resolution.of(Type.UNVERIFIED_EMAIL, null, null);
        }

        final String email;
        try {
            email = EmailPolicy.normalizeAndValidate(providerEmail);
        } catch (RegistrationValidationException exception) {
            // provider 가 준 값이라도 우리 정책을 통과하지 못하면 인증된 이메일로 쓰지 않는다.
            return Resolution.of(Type.UNVERIFIED_EMAIL, null, null);
        }
        return resolveByEmail(email, true);
    }

    /**
     * @param mayFinalizeWithdrawal 유예가 끝난 계정을 파기해도 되는 첫 판정인지 여부.
     *                              파기 후 재판정에서는 false 라 무한 재귀가 생기지 않는다.
     */
    private Resolution resolveByEmail(String email, boolean mayFinalizeWithdrawal) {
        User existing = userMapper.findByEmail(email);
        if (existing == null || existing.getId() == null || existing.getStatus() == null) {
            return Resolution.of(Type.NEW_ACCOUNT, email, null);
        }

        return switch (existing.getStatus()) {
            // RESTRICTED 도 기존 로그인 정책상 인증까지는 되는 계정이라 연결 확인을 허용한다.
            // 제재 격리는 로그인 후 RestrictedAccountFilter 가 그대로 맡는다.
            case ACTIVE, RESTRICTED ->
                    Resolution.of(Type.LINK_EXISTING, email, existing.getId());
            // 이메일 인증 대기 계정을 소셜 로그인으로 조용히 활성화하거나 가져가지 않는다.
            case INACTIVE -> Resolution.of(Type.VERIFICATION_PENDING, email, null);
            case WITHDRAWAL_PENDING ->
                    resolveWithdrawalPending(email, existing.getId(), mayFinalizeWithdrawal);
            // 휴면·최종탈퇴 계정으로는 우회 가입도 연결도 하지 않는다.
            case SUSPENDED, DEACTIVATED -> Resolution.of(Type.BLOCKED, email, null);
        };
    }

    private Resolution resolveWithdrawalPending(String email,
                                                Long userId,
                                                boolean mayFinalizeWithdrawal) {
        if (!mayFinalizeWithdrawal) {
            return Resolution.of(Type.WITHDRAWAL_PENDING, email, null);
        }
        if (withdrawalGraceService.resolveAccess(userId, null)
                == WithdrawalGraceService.Outcome.IN_GRACE) {
            // 유예 30일 동안 이메일은 그 계정이 계속 점유한다.
            return Resolution.of(Type.WITHDRAWAL_PENDING, email, null);
        }
        // 파기됐다면 user_email 이 익명 주소로 바뀌어 더는 이 이메일을 점유하지 않는다.
        return resolveByEmail(email, false);
    }

    /**
     * 사용자가 social-signup 폼에 직접 입력한 이메일의 가입 가능 여부.
     *
     * <p>{@link #resolve} 와 달리 본인 확인이 끝난 상태가 아니므로 탈퇴 유예 계정을 여기서
     * 파기하지 않는다. 유예가 끝난 계정은 기존 배치가 정리할 때까지 이메일을 계속 점유한다.
     */
    public EnteredEmail classifyEnteredEmail(String email) {
        final String normalized;
        try {
            normalized = EmailPolicy.normalizeAndValidate(email);
        } catch (RegistrationValidationException exception) {
            return new EnteredEmail(EnteredEmailStatus.INVALID, null);
        }

        User existing = userMapper.findByEmail(normalized);
        if (existing == null || existing.getStatus() == null) {
            return new EnteredEmail(EnteredEmailStatus.AVAILABLE, normalized);
        }
        // ACTIVE 는 다음 단계의 기존 계정 연결 대상이라 안내 문구를 구분한다.
        // 나머지 상태는 신규가입 우회 통로가 되지 않도록 한 가지 안내로 묶는다.
        return existing.getStatus() == UserStatus.ACTIVE
                ? new EnteredEmail(EnteredEmailStatus.EXISTING_ACTIVE, normalized)
                : new EnteredEmail(EnteredEmailStatus.UNAVAILABLE, normalized);
    }

    public enum EnteredEmailStatus {
        /** 쓰는 계정이 없다. 이번 단계에서 신규 가입할 수 있다. */
        AVAILABLE,
        /** 이미 가입된 계정이 있다. 연결은 다음 단계에서 지원한다. */
        EXISTING_ACTIVE,
        /** 인증 대기·탈퇴 유예·휴면·제재 등으로 지금은 쓸 수 없다. */
        UNAVAILABLE,
        /** 이메일 형식이 우리 정책을 통과하지 못한다. */
        INVALID
    }

    /** @param email 정규화된 이메일. INVALID 에서는 null */
    public record EnteredEmail(EnteredEmailStatus status, String email) {
    }

    public enum Type {
        /** Google 이 아니다. 기존 신규가입 흐름을 그대로 쓴다. */
        NOT_APPLICABLE,
        /** Google 인데 인증된 이메일이 없다. 임의로 가입시키지 않는다. */
        UNVERIFIED_EMAIL,
        /** 같은 이메일의 계정이 없다. 이 이메일로 신규 가입한다. */
        NEW_ACCOUNT,
        /** 같은 이메일의 이용 가능한 계정이 있다. 본인 확인 후 연결한다. */
        LINK_EXISTING,
        /** 같은 이메일의 계정이 탈퇴 유예 중이다. 복구 안내로 보낸다. */
        WITHDRAWAL_PENDING,
        /** 같은 이메일의 계정이 이메일 인증 대기 중이다. 기존 가입 완료 안내로 보낸다. */
        VERIFICATION_PENDING,
        /** 같은 이메일의 계정이 휴면·최종탈퇴 상태다. 진행하지 않는다. */
        BLOCKED
    }

    /**
     * @param email          정규화된 이메일. NOT_APPLICABLE / UNVERIFIED_EMAIL 에서는 null
     * @param existingUserId LINK_EXISTING 에서만 채워진다
     */
    public record Resolution(Type type, String email, Long existingUserId) {

        static Resolution of(Type type, String email, Long existingUserId) {
            return new Resolution(type, email, existingUserId);
        }
    }
}
