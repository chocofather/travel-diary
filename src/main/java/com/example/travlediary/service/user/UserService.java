package com.example.travlediary.service.user;

import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailDispatchService;
import com.example.travlediary.service.email.EmailVerificationService;
import com.example.travlediary.service.file.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {
    public static final String INVALID_RESET_TOKEN_MESSAGE =
            "만료되었거나 잘못된 토큰입니다.";
    public static final String SAME_AS_CURRENT_PASSWORD_MESSAGE =
            "현재 사용 중인 비밀번호와 다른 비밀번호를 입력해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileUploadService fileUploadService;
    private final EmailDispatchService emailDispatchService;
    private final EmailVerificationService emailVerificationService;

    @Value("${custom.server-url}")
    private String serverUrl;

    @Autowired
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       FileUploadService fileUploadService,
                       EmailDispatchService emailDispatchService,
                       EmailVerificationService emailVerificationService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileUploadService = fileUploadService;
        this.emailDispatchService = emailDispatchService;
        this.emailVerificationService = emailVerificationService;
    }

    // 🔐 로그인 기능
    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }

        // 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getUserPassword())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    // 📝 회원가입 기능
    public RegistrationResult registerUser(RegistrationForm form) {
        String username = form.getUsername().strip();
        String email = EmailPolicy.normalizeAndValidate(form.getUserEmail());
        String nickname = NicknamePolicy.normalizeAndValidate(form.getNickname());
        String rawPassword = form.getUserPassword();

        PasswordPolicy.validate(rawPassword);
        if (!rawPassword.equals(form.getPasswordConfirm())) {
            throw new RegistrationValidationException(
                    "passwordConfirm", "비밀번호가 일치하지 않습니다.");
        }

        validateRegistrationDuplicates(username, email, nickname);

        User user = new User();
        user.setUsername(username);
        user.setUserEmail(email);
        user.setNickname(nickname);
        user.setFullName(FullNamePolicy.normalizeAndValidate(form.getFullName()));
        user.setUserPhone(normalizeOptional(form.getUserPhone()));
        user.setUserBirth(form.getUserBirth());

        user.setUserPassword(passwordEncoder.encode(rawPassword));

        // ✅ 기본 사용자 정보 설정
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.INACTIVE);
        user.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

        emailVerificationService.initializeVerification(user);

        // 📷 프로필 이미지 업로드 처리
        try {
            if (form.getProfileImageFile() != null && !form.getProfileImageFile().isEmpty()) {
                String imagePath = fileUploadService.saveFile(form.getProfileImageFile());
                user.setProfileImage(imagePath);
            } else {
                user.setProfileImage("uploads/default.png"); // 기본 프로필 이미지 설정
            }
        } catch (Exception e) {
            log.warn("Registration profile image could not be stored; using the default image: exceptionType={}",
                    e.getClass().getSimpleName());
            user.setProfileImage("uploads/default.png");
        }

        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationValidationException(
                    "registration", "이미 사용 중인 회원가입 정보가 있습니다.");
        }
        log.info("Registration user stored: userId={}, recipient={}",
                user.getId(), EmailPolicy.mask(email));

        boolean emailRequested = emailVerificationService.requestInitialVerification(user);
        log.info("Registration verification email dispatch completed: userId={}, requested={}",
                user.getId(), emailRequested);
        return new RegistrationResult(email, emailRequested);
    }

    private void validateRegistrationDuplicates(String username, String email, String nickname) {
        if (userMapper.countByUsername(username) > 0) {
            throw new RegistrationValidationException("username", "이미 사용 중인 아이디입니다.");
        }
        if (userMapper.findByEmail(email) != null) {
            throw new RegistrationValidationException("userEmail", "이미 사용 중인 이메일입니다.");
        }
        if (userMapper.countByNickname(nickname) > 0) {
            throw new RegistrationValidationException("nickname", "이미 사용 중인 닉네임입니다.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }


    // 🧐 사용자 조회 (null 체크 포함)
    public User findByUsername(String username) {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        return user;
    }

    // 📌 아이디 중복 검사
    public boolean isUsernameExists(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    // 🏷 닉네임 중복 검사
    public boolean isNicknameExists(String nickname) {
        String normalized = NicknamePolicy.normalizeAndValidate(nickname);
        return userMapper.countByNickname(normalized) > 0;
    }

    // 이메일 중복검사
    public boolean isEmailExists(String email) {
        return userMapper.findByEmail(EmailPolicy.normalizeAndValidate(email)) != null;
    }

    // 프로필 이미지
    public String getProfileImage(User user) {
        if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
            return "/images/default.png";
        }
        return user.getProfileImage();
    }

    /* ================= [ 아이디 찾기 메일 ] ================= */
    public void processFindUsername(String email) {
        String normalizedEmail = EmailPolicy.normalizeAndValidate(email);
        User u = userMapper.findActiveByEmailForUsernameRecovery(normalizedEmail);
        if (u == null) {
            return;
        }

        dispatchUsernameRecoveryEmail(normalizedEmail, u.getUsername());
    }

    /* =========== [ 비밀번호 재설정 링크 발송 ] =========== */
    public void processResetPasswordRequest(String username, String email) {
        String normalizedEmail = EmailPolicy.normalizeAndValidate(email);
        User u = userMapper.findByUsernameAndEmail(username.strip(), normalizedEmail);
        if (u == null) {
            return;
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = ResetTokenHasher.hash(rawToken);
        LocalDateTime exp = LocalDateTime.now().plusMinutes(30);

        userMapper.updateResetToken(u.getId(), tokenHash, exp);

        String link = serverUrl + "/users/reset-password?token=" + rawToken;
        dispatchPasswordResetEmail(normalizedEmail, link);
    }

    private void dispatchUsernameRecoveryEmail(String recipient, String username) {
        try {
            emailDispatchService.dispatchUsernameRecoveryEmail(
                    recipient,
                    username,
                    serverUrl + "/login",
                    serverUrl + "/users/find-password");
        } catch (RuntimeException exception) {
            log.error("Username recovery email could not be scheduled: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void dispatchPasswordResetEmail(String recipient, String resetUrl) {
        try {
            emailDispatchService.dispatchPasswordResetEmail(recipient, resetUrl);
        } catch (RuntimeException exception) {
            log.error("Password reset email could not be scheduled: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    /* =========== [ 토큰 검증 ] =========== */
    public User validateResetToken(String rawToken) {
        String tokenHash = ResetTokenHasher.hash(rawToken);
        User u = userMapper.findByResetToken(tokenHash);
        if (u == null) return null;
        return u.getResetTokenExp().isAfter(LocalDateTime.now()) ? u : null;
    }

    /* =========== [ 실제 비밀번호 변경 ] =========== */
    public void resetPassword(String rawToken, String rawPw) {
        resetPassword(rawToken, rawPw, rawPw);
    }

    public void resetPassword(String rawToken, String rawPw, String passwordConfirmation) {
        User u = validateResetToken(rawToken);
        if (u == null) throw new IllegalArgumentException(INVALID_RESET_TOKEN_MESSAGE);

        PasswordPolicy.validate(rawPw);
        PasswordPolicy.validateConfirmation(rawPw, passwordConfirmation);
        if (passwordEncoder.matches(rawPw, u.getUserPassword())) {
            throw new IllegalArgumentException(SAME_AS_CURRENT_PASSWORD_MESSAGE);
        }
        String encPw = passwordEncoder.encode(rawPw);
        userMapper.updateUserPassword(u.getId(), encPw);
        userMapper.clearResetToken(u.getId());   // 1회 사용 후 폐기
    }
}
