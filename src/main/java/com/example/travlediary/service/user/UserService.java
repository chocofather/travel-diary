package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailService;
import com.example.travlediary.service.file.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileUploadService fileUploadService;
    private final EmailService emailService; // 이메일 전송 서비스 주입

    @Value("${custom.server-url}")
    private String serverUrl;

    @Autowired
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       FileUploadService fileUploadService, EmailService emailService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileUploadService = fileUploadService;
        this.emailService = emailService;
    }

    // 🔐 로그인 기능
    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            System.out.println("❌ 로그인 실패 - 사용자를 찾을 수 없습니다: " + username);
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }

        System.out.println("🔍 DB에서 찾은 사용자: " + user.getUsername());
        System.out.println("🔐 DB에 저장된 암호화된 비밀번호: " + user.getUserPassword());
        System.out.println("🔐 입력된 비밀번호: " + password);

        // 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getUserPassword())) {
            System.out.println("❌ 비밀번호 불일치!");
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        System.out.println("✅ 로그인 성공!");
        return user;
    }

    // 📝 회원가입 기능
    public void registerUser(User user, MultipartFile profileImageFile) {
        String rawPassword = user.getUserPassword();

        // ✅ 비밀번호 유효성 검사 (8자 이상, 특수문자 포함)
        if (rawPassword == null || !rawPassword.matches("^(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$")) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이며, 특수문자 1개 이상 포함해야 합니다.");
        }

        // ✅ 암호화된 비밀번호가 아닌 경우만 암호화
        if (!rawPassword.startsWith("$2a$")) {
            user.setUserPassword(passwordEncoder.encode(rawPassword));
        }

        // ✅ 기본 사용자 정보 설정
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.INACTIVE);
        user.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

        // ✅ 이메일 인증 토큰 생성
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);

        // 📷 프로필 이미지 업로드 처리
        try {
            if (profileImageFile != null && !profileImageFile.isEmpty()) {
                String imagePath = fileUploadService.saveFile(profileImageFile);
                user.setProfileImage(imagePath);
            } else {
                user.setProfileImage("uploads/default.png"); // 기본 프로필 이미지 설정
            }
        } catch (Exception e) {
            System.out.println("⚠ 프로필 이미지 저장 실패! 기본 이미지 사용");
            user.setProfileImage("uploads/default.png");

        }

        System.out.println("✅ DB 저장 전 데이터: " + user);
        userMapper.insertUser(user);

        // ✅ 이메일 인증 메일 전송
        String subject = "여행일기 이메일 인증";
        String verificationLink = "http://localhost:8080/users/verify?token=" + token;
        String body = "회원가입을 완료하려면 아래 링크를 클릭하세요:\n" + verificationLink;

        emailService.sendVerificationEmail(user.getUserEmail(), subject, token);

    }


    // 🧐 사용자 조회 (null 체크 포함)
    public User findByUsername(String username) {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            System.out.println("❌ 사용자 찾기 실패 - DB에 존재하지 않음: " + username);
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        System.out.println("✅ 사용자 찾기 성공 - username: " + user.getUsername());
        System.out.println("🔐 DB에 저장된 암호화된 비밀번호: " + user.getUserPassword());

        return user;
    }

    // 📌 아이디 중복 검사
    public boolean isUsernameExists(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    // 🏷 닉네임 중복 검사
    public boolean isNicknameExists(String nickname) {
        return userMapper.countByNickname(nickname) > 0;
    }

    public User findByVerificationToken(String token) {
        return userMapper.findByVerificationToken(token);
    }

    // 이메일 중복검사
    public boolean isEmailExists(String email) {
        return userMapper.findByEmail(email) != null;
    }

    // 프로필 이미지
    public String getProfileImage(User user) {
        if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
            return "/images/default.png";
        }
        return user.getProfileImage();
    }

    /* ================= [ 아이디 찾기 메일 ] ================= */
    public void processFindUsername(String fullName, String email) {
        User u = userMapper.findByFullNameAndEmail(fullName, email);
        if (u == null) throw new IllegalArgumentException("정보가 일치하지 않습니다.");

        String html = """
          <h3>아이디 찾기 결과</h3>
          <p>회원님의 아이디는 <strong>%s</strong> 입니다.</p>
          <hr>
          <p>비밀번호가 기억나지 않으시면
             <a href="%s/users/find-password">여기</a>를 클릭해 재설정하세요.</p>
        """.formatted(u.getUsername(), serverUrl);

        emailService.sendEmail(email, "[여행일기] 아이디 안내", html);
    }

    /* =========== [ 비밀번호 재설정 링크 발송 ] =========== */
    public void processResetPasswordRequest(String username, String email) {
        User u = userMapper.findByUsernameAndEmail(username, email);
        if (u == null) throw new IllegalArgumentException("정보가 일치하지 않습니다.");

        String token = UUID.randomUUID().toString();
        LocalDateTime exp = LocalDateTime.now().plusMinutes(30);

        userMapper.updateResetToken(u.getId(), token, exp);

        String link = serverUrl + "/users/reset-password?token=" + token;
        String html = """
          <h3 style="margin:0 0 12px 0;font-size:20px;font-weight:700;color:#222;">비밀번호 재설정</h3>
        
          <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:#555;">
             아래 버튼을 눌러 <strong>30분 이내</strong>에 새 비밀번호를 설정하세요.
          </p>
        
          <p style="margin:0 0 32px 0;">
             <a href="%s"
                style="display:inline-block;
                       padding:14px 32px;
                       background:#0066cc;             /* 메인 색상 */
                       color:#ffffff !important;        /* 글자색 */
                       font-size:16px;
                       font-weight:600;
                       text-decoration:none;
                       border-radius:6px;               /* 둥글게 */
                       box-shadow:0 3px 8px rgba(0,0,0,.15);
                       transition:background .2s ease;">
                비밀번호 재설정
             </a>
          </p>
          """.formatted(link);
        emailService.sendEmail(email, "[여행일기] 비밀번호 재설정 링크", html);
    }

    /* =========== [ 토큰 검증 ] =========== */
    public User validateResetToken(String token) {
        User u = userMapper.findByResetToken(token);
        if (u == null) return null;
        return u.getResetTokenExp().isAfter(LocalDateTime.now()) ? u : null;
    }

    /* =========== [ 실제 비밀번호 변경 ] =========== */
    public void resetPassword(String token, String rawPw) {
        User u = validateResetToken(token);
        if (u == null) throw new IllegalArgumentException("만료되었거나 잘못된 토큰입니다.");

        String encPw = passwordEncoder.encode(rawPw);
        userMapper.updateUserPassword(u.getId(), encPw);
        userMapper.clearResetToken(u.getId());   // 1회 사용 후 폐기
    }
}