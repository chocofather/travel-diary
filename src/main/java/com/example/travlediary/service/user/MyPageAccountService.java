package com.example.travlediary.service.user;

import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.AccountEditForm;
import com.example.travlediary.dto.PasswordChangeForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MyPageAccountService {

    private static final int MAX_NAME_LENGTH = 50;

    private final UserMapper userMapper;
    private final AccountAnonymizationService accountAnonymizationService;
    private final PasswordEncoder passwordEncoder;

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
        LocalDate userBirth = validateBirth(form.getUserBirth());

        int updated = userMapper.updateAccountDetails(userId, fullName, userPhone, userBirth);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }

        form.setFullName(fullName);
        form.setUserPhone(userPhone);
        form.setUserBirth(userBirth);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeForm form) {
        if (form.getNewPassword() == null || form.getNewPassword().isEmpty()) {
            throw new AccountValidationException(
                    "newPassword", "새 비밀번호를 입력해주세요.");
        }
        if (form.getNewPasswordConfirm() == null || form.getNewPasswordConfirm().isEmpty()) {
            throw new AccountValidationException(
                    "newPasswordConfirm", "새 비밀번호 확인을 입력해주세요.");
        }
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            throw new AccountValidationException(
                    "newPasswordConfirm", "새 비밀번호가 일치하지 않습니다.");
        }
        try {
            PasswordPolicy.validate(form.getNewPassword());
        } catch (IllegalArgumentException exception) {
            throw new AccountValidationException("newPassword", exception.getMessage());
        }

        String encodedPassword = passwordEncoder.encode(form.getNewPassword());
        if (userMapper.updateActiveUserPassword(userId, encodedPassword) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void withdraw(Long userId, String currentPassword) {
        User account = userMapper.findActiveAccountSecurityByIdForUpdate(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        if (account.getUserRole() != UserRole.USER) {
            throw new AccountValidationException(
                    null, "관리자 계정은 마이페이지에서 탈퇴할 수 없습니다.");
        }
        if (currentPassword == null
                || account.getUserPassword() == null
                || !passwordEncoder.matches(currentPassword, account.getUserPassword())) {
            throw new AccountValidationException(
                    "currentPassword", "비밀번호가 일치하지 않습니다.");
        }

        String withdrawnEmail = accountAnonymizationService.anonymizedEmail(userId);
        String withdrawnNickname = accountAnonymizationService.anonymizedNickname();

        accountAnonymizationService.clearPersonalTraces(userId);

        int updated = userMapper.deactivateAccount(
                userId, withdrawnEmail, withdrawnNickname, UserStatus.DEACTIVATED);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "회원 탈퇴를 완료할 수 없습니다.");
        }
    }

    private String normalizeName(String fullName) {
        String normalized = fullName == null ? "" : fullName.strip();
        if (normalized.isEmpty()) {
            throw new AccountValidationException("fullName", "이름을 입력해주세요.");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new AccountValidationException("fullName", "이름은 50자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String normalizePhone(String userPhone) {
        if (userPhone == null || userPhone.isBlank()) {
            return null;
        }
        String value = userPhone.strip();
        if (!value.matches("[0-9-]+")) {
            throw new AccountValidationException(
                    "userPhone", "전화번호 형식을 확인해주세요.");
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
        throw new AccountValidationException("userPhone", "전화번호 형식을 확인해주세요.");
    }

    private LocalDate validateBirth(LocalDate userBirth) {
        if (userBirth == null) {
            throw new AccountValidationException("userBirth", "생년월일을 입력해주세요.");
        }
        if (userBirth.isAfter(LocalDate.now())) {
            throw new AccountValidationException(
                    "userBirth", "생년월일은 미래 날짜를 선택할 수 없습니다.");
        }
        return userBirth;
    }

}
