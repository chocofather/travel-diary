package com.example.travlediary.service.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private static final String DUPLICATE_NICKNAME_MESSAGE =
            "이미 사용 중인 닉네임입니다.";

    private final UserMapper userMapper;
    private final SocialAccountMapper socialAccountMapper;

    @Transactional
    public long complete(PendingSocialSignup pending, SocialSignupForm form) {
        validatePending(pending);

        if (socialAccountMapper.findByProviderAndProviderUserId(
                pending.provider(), pending.providerUserId()) != null) {
            throw new SocialSignupFlowException("이미 처리된 소셜 가입 정보입니다.");
        }

        String nickname = validateForm(form);
        if (userMapper.countByNickname(nickname) > 0) {
            throw new SocialSignupValidationException(
                    "nickname", DUPLICATE_NICKNAME_MESSAGE,
                    "signup.error.nickname.duplicate");
        }

        String userEmail = resolveUserEmail(pending);
        // 가입 화면을 여는 사이 같은 이메일 계정이 생겼을 수 있다. UNIQUE 예외를 기다리지 않고 먼저 본다.
        if (userEmail != null && userMapper.findByEmail(userEmail) != null) {
            throw new SocialSignupFlowException("이미 같은 이메일로 가입된 계정이 있습니다.");
        }

        User user = new User();
        user.setNickname(nickname);
        user.setUserEmail(userEmail);
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Timestamp.from(Instant.now()));

        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException exception) {
            // user_email 도 UNIQUE 라 닉네임이 아니라 이메일 경합일 수 있다.
            if (userEmail != null && userMapper.findByEmail(userEmail) != null) {
                throw new SocialSignupFlowException("이미 같은 이메일로 가입된 계정이 있습니다.");
            }
            throw new SocialSignupValidationException(
                    "nickname", DUPLICATE_NICKNAME_MESSAGE,
                    "signup.error.nickname.duplicate");
        }
        if (user.getId() == null) {
            throw new SocialSignupPersistenceException("회원 정보를 저장하지 못했습니다.");
        }

        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUserId(user.getId());
        socialAccount.setProvider(pending.provider());
        socialAccount.setProviderUserId(pending.providerUserId());
        socialAccount.setProviderEmail(normalizeReferenceEmail(pending.providerEmail()));
        socialAccount.setProviderEmailVerified(pending.providerEmailVerified());

        try {
            if (socialAccountMapper.insert(socialAccount) != 1) {
                throw new SocialSignupPersistenceException("소셜 계정을 연결하지 못했습니다.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SocialSignupFlowException("이미 처리된 소셜 가입 정보입니다.");
        }
        return user.getId();
    }

    private void validatePending(PendingSocialSignup pending) {
        Instant now = Instant.now();
        if (pending == null
                || !isSupportedProvider(pending.provider())
                || isBlank(pending.flowId())
                || isBlank(pending.providerUserId())
                || pending.createdAt() == null
                || pending.isExpired(now)) {
            throw new SocialSignupFlowException("소셜 로그인 정보가 만료되었습니다.");
        }
    }

    private String validateForm(SocialSignupForm form) {
        if (form == null) {
            throw new SocialSignupValidationException(
                    "nickname", "닉네임을 입력해주세요.", "signup.error.nickname.required");
        }
        if (!form.isTermsAccepted()) {
            throw new SocialSignupValidationException(
                    "termsAccepted", "서비스 이용약관에 동의해주세요.", "signup.error.terms.service");
        }
        if (!form.isPrivacyAccepted()) {
            throw new SocialSignupValidationException(
                    "privacyAccepted", "개인정보 수집 및 이용에 동의해주세요.",
                    "signup.error.terms.privacy");
        }
        try {
            return NicknamePolicy.normalizeAndValidate(form.getNickname());
        } catch (NicknamePolicy.ViolationException exception) {
            // 형식/금칙어 구분은 일반 회원가입과 같은 key 를 쓴다.
            String messageCode =
                    exception.getViolationType() == NicknamePolicy.ViolationType.FORBIDDEN
                            ? "signup.error.nickname.forbidden"
                            : "signup.error.nickname.invalid";
            throw new SocialSignupValidationException(
                    "nickname", exception.getMessage(), messageCode);
        }
    }

    /**
     * users.user_email 에 저장할 공식 이메일.
     *
     * <p>Google 은 OIDC 가 email_verified 를 함께 주므로 그 이메일을 인증된 이메일로 신뢰한다.
     * Kakao/Naver 는 아직 이메일 확보·인증 수단이 없어 기존대로 비워 둔다.
     */
    private String resolveUserEmail(PendingSocialSignup pending) {
        if (pending.provider() != SocialProvider.GOOGLE) {
            return null;
        }
        if (isBlank(pending.providerEmail())
                || !Boolean.TRUE.equals(pending.providerEmailVerified())) {
            throw new SocialSignupFlowException("인증된 소셜 이메일을 확인할 수 없습니다.");
        }
        try {
            return EmailPolicy.normalizeAndValidate(pending.providerEmail());
        } catch (RegistrationValidationException exception) {
            throw new SocialSignupFlowException("인증된 소셜 이메일을 확인할 수 없습니다.");
        }
    }

    private String normalizeReferenceEmail(String email) {
        return isBlank(email) ? null : email.strip();
    }

    private boolean isSupportedProvider(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE
                || provider == SocialProvider.KAKAO
                || provider == SocialProvider.NAVER;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
