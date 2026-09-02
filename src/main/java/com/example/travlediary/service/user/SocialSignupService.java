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
                    "nickname", DUPLICATE_NICKNAME_MESSAGE);
        }

        User user = new User();
        user.setNickname(nickname);
        user.setUserEmail(null);
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Timestamp.from(Instant.now()));

        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException exception) {
            throw new SocialSignupValidationException(
                    "nickname", DUPLICATE_NICKNAME_MESSAGE);
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
                    "nickname", "닉네임을 입력해주세요.");
        }
        if (!form.isTermsAccepted()) {
            throw new SocialSignupValidationException(
                    "termsAccepted", "서비스 이용약관에 동의해주세요.");
        }
        if (!form.isPrivacyAccepted()) {
            throw new SocialSignupValidationException(
                    "privacyAccepted", "개인정보 수집 및 이용에 동의해주세요.");
        }
        try {
            return NicknamePolicy.normalizeAndValidate(form.getNickname());
        } catch (NicknamePolicy.ViolationException exception) {
            throw new SocialSignupValidationException("nickname", exception.getMessage());
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
