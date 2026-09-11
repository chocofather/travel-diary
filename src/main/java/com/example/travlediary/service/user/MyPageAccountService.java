package com.example.travlediary.service.user;

import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.AccountEditForm;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class MyPageAccountService {

    private static final int MAX_NAME_LENGTH = 50;
    /** 탈퇴 신청 후 최종 파기까지의 보존 기간. */
    public static final Duration WITHDRAWAL_GRACE_PERIOD = Duration.ofDays(30);

    private final UserMapper userMapper;
    private final AccountAnonymizationService accountAnonymizationService;
    private final PasswordEncoder passwordEncoder;
    private final SocialAccountMapper socialAccountMapper;
    private final MessageSource messageSource;
    private final Clock clock;

    @Autowired
    public MyPageAccountService(UserMapper userMapper,
                                AccountAnonymizationService accountAnonymizationService,
                                PasswordEncoder passwordEncoder,
                                SocialAccountMapper socialAccountMapper,
                                MessageSource messageSource) {
        this(userMapper, accountAnonymizationService, passwordEncoder, socialAccountMapper,
                messageSource, Clock.systemDefaultZone());
    }

    MyPageAccountService(UserMapper userMapper,
                         AccountAnonymizationService accountAnonymizationService,
                         PasswordEncoder passwordEncoder,
                         SocialAccountMapper socialAccountMapper,
                         MessageSource messageSource,
                         Clock clock) {
        this.userMapper = userMapper;
        this.accountAnonymizationService = accountAnonymizationService;
        this.passwordEncoder = passwordEncoder;
        this.socialAccountMapper = socialAccountMapper;
        this.messageSource = messageSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean hasLocalPassword(Long userId) {
        return userMapper.hasLocalPasswordById(userId);
    }

    @Transactional(readOnly = true)
    public AccountDetailsDto getAccountDetails(Long userId) {
        AccountDetailsDto details = userMapper.findAccountDetailsById(userId);
        if (details == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        return details;
    }

    @Transactional(readOnly = true)
    public boolean verifyCurrentPassword(Long userId, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        User account = userMapper.findActiveAccountSecurityById(userId);
        return account != null
                && account.getUserPassword() != null
                && passwordEncoder.matches(rawPassword, account.getUserPassword());
    }

    @Transactional
    public void updateAccountDetails(Long userId, AccountEditForm form) {
        String fullName = normalizeName(form.getFullName());
        String userPhone = normalizePhone(form.getUserPhone());

        // 생년월일은 이 경로에서 건드리지 않는다. 가입 때 저장된 값이 그대로 남는다.
        int updated = userMapper.updateAccountDetails(userId, fullName, userPhone);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }

        form.setFullName(fullName);
        form.setUserPhone(userPhone);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeForm form) {
        if (form.getNewPassword() == null || form.getNewPassword().isEmpty()) {
            throw new AccountValidationException("newPassword",
                    "mypage.account.error.password.required", "새 비밀번호를 입력해주세요.");
        }
        if (form.getNewPasswordConfirm() == null || form.getNewPasswordConfirm().isEmpty()) {
            throw new AccountValidationException("newPasswordConfirm",
                    "mypage.account.error.password.confirmRequired",
                    "새 비밀번호 확인을 입력해주세요.");
        }
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            throw new AccountValidationException("newPasswordConfirm",
                    "mypage.account.error.password.mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        try {
            PasswordPolicy.validate(form.getNewPassword());
        } catch (IllegalArgumentException exception) {
            // 정책 문구는 가입 화면과 공유하므로 여기서는 코드만 얹고 원문을 기본값으로 남긴다.
            throw new AccountValidationException("newPassword",
                    "mypage.account.error.password.invalid", exception.getMessage());
        }

        String encodedPassword = passwordEncoder.encode(form.getNewPassword());
        if (userMapper.updateActiveUserPassword(userId, encodedPassword) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
    }

    /**
     * 회원탈퇴 신청.
     *
     * <p>본인 확인은 계정 및 보안 진입 단계의 재인증이 이미 끝냈으므로 여기서 비밀번호를 다시 받지 않는다.
     * 재인증이 없거나 만료된 요청은 컨트롤러의 requireVerification 이 먼저 막는다.
     * 이 단계에서는 실수 방지를 위한 확인 문구만 서버에서 다시 확인한다.
     */
    @Transactional
    public void withdraw(Long userId, String confirmationPhrase) {
        User account = userMapper.findActiveAccountSecurityByIdForUpdate(userId);
        validateWithdrawableAccount(account);
        if (!WithdrawalConfirmationPolicy.matches(confirmationPhrase, messageSource)) {
            throw new AccountValidationException("confirmationPhrase",
                    "mypage.account.error.withdrawal.confirm.mismatch",
                    "확인 문구가 일치하지 않습니다.");
        }

        requestWithdrawal(account);
    }

    @Transactional
    public void withdrawAfterSocialReauthentication(Long userId) {
        User account = userMapper.findActiveAccountSecurityByIdForUpdate(userId);
        validateWithdrawableAccount(account);
        if (account.getUserPassword() != null) {
            throw new AccountValidationException(
                    null, "소셜 계정 탈퇴를 처리할 수 없습니다.");
        }

        // 30일 유예 동안에는 social_accounts 도 provider 연결도 그대로 둔다.
        requestWithdrawal(account);
    }

    private void validateWithdrawableAccount(User account) {
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        if (account.getUserRole() != UserRole.USER) {
            throw new AccountValidationException(null,
                    "mypage.account.withdrawal.adminBlocked",
                    "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.");
        }
    }

    /**
     * 탈퇴 신청. 상태와 유예 일정만 남기고 개인정보·콘텐츠·소셜 연결은 전부 보존한다.
     * 익명화와 개인 흔적 정리는 30일 뒤 최종 파기 단계의 몫이라 여기서는 하지 않는다.
     */
    private void requestWithdrawal(User account) {
        LocalDateTime requestedAt = LocalDateTime.now(clock);
        int updated = userMapper.requestWithdrawal(
                account.getId(),
                UserStatus.WITHDRAWAL_PENDING,
                requestedAt,
                requestedAt.plus(WITHDRAWAL_GRACE_PERIOD));
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "회원 탈퇴를 완료할 수 없습니다.");
        }
    }

    private String normalizeName(String fullName) {
        String normalized = fullName == null ? "" : fullName.strip();
        if (normalized.isEmpty()) {
            throw new AccountValidationException("fullName",
                    "mypage.account.error.fullName.required", "이름을 입력해주세요.");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new AccountValidationException("fullName",
                    "mypage.account.error.fullName.tooLong", "이름은 50자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String normalizePhone(String userPhone) {
        if (userPhone == null || userPhone.isBlank()) {
            return null;
        }
        String value = userPhone.strip();
        if (!value.matches("[0-9-]+")) {
            throw invalidPhone();
        }
        String digits = value.replace("-", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6)
                    + "-" + digits.substring(6);
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7)
                    + "-" + digits.substring(7);
        }
        throw invalidPhone();
    }

    private AccountValidationException invalidPhone() {
        return new AccountValidationException("userPhone",
                "mypage.account.error.phone.invalid", "전화번호 형식을 확인해주세요.");
    }

}
