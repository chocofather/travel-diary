package com.example.travlediary.controller.user;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailService;
import com.example.travlediary.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.util.HashMap;
import java.util.Map;


@Controller
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailService emailService;

    @Autowired
    public UserController(UserService userService, UserMapper userMapper, EmailService emailService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.emailService = emailService;
    }

    // 회원가입 폼 화면
    @GetMapping("/register")
    public String showRegisterForm() {
       // model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user,
                               @RequestParam("passwordConfirm") String passwordConfirm,
                               @RequestParam(value = "profileImageFile", required = false) MultipartFile profileImage,
                               Model model) {

        System.out.println("회원가입 시도: " + user);
        System.out.println("비밀번호 확인 값: " + passwordConfirm);

        // 비밀번호 확인
        if (!user.getUserPassword().equals(passwordConfirm)) {
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
            model.addAttribute("user", user);
            return "register";
        }

        try {
            // ✅ 회원가입 로직을 UserService에서 처리
            userService.registerUser(user, profileImage);

            return "redirect:/users/register/verify-waiting";  // ✅ 이메일 인증 대기 페이지로 리다이렉트

        } catch (Exception e) {
            model.addAttribute("error", "회원가입 실패: " + e.getMessage());
            model.addAttribute("user", user);
            return "register";
        }
    }

    // ✅ 아이디 중복 검사 API (AJAX 요청 처리)
    @GetMapping("/check-username")
    @ResponseBody
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        System.out.println("check-username API 호출됨. 입력값: " + username); // ✅ 디버깅 로그 추가

        boolean exists = false;
        try {
            exists = userService.isUsernameExists(username);
        } catch (Exception e) {
            e.printStackTrace(); // ✅ 오류 로그 출력
        }
        // boolean exists = userService.isUsernameExists(username);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return response;
    }

    // 비밀번호 검증 API
    @PostMapping("/validate-password")
    public ResponseEntity<String> validatePassword(@RequestBody PasswordRequest request) {
        String password = request.getPassword();

        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body("비밀번호를 입력하세요.");
        }

        String regex = "^(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$";

        if (!password.matches(regex)) {
            return ResponseEntity.badRequest().body("비밀번호는 8자 이상, 특수문자 1개 이상 포함해야 합니다.");
        }

        return ResponseEntity.ok("사용 가능한 비밀번호입니다.");
    }

    // ⚠️ JSON 데이터를 받을 클래스
    public static class PasswordRequest {
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    // ✅ 이메일 인증 처리
    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token) {
        System.out.println("📩 이메일 인증 요청 token: " + token); // 🔍 여기 찍히는 값 확인

        User user = userService.findByVerificationToken(token);

        if (user == null) {
            return "redirect:/login?error=invalid_token";
        }

        user.setStatus(UserStatus.ACTIVE); // 이메일 인증 완료
        user.setVerificationToken(null);    // 토큰 제거
        userMapper.updateUser(user);        // DB 업데이트

        return "redirect:/login?verified=true";
    }

    // 이메일인증 대기 페이지
    @GetMapping("/register/verify-waiting")
    public String showVerifyWaitingPage() {
        return "verify-waiting"; // verify-waiting.html로 이동
    }

    // 아이디 찾기
    @GetMapping("/find-username")
    public String showFindUsername() {          // GET  폼
        return "find-username";
    }

    @PostMapping("/find-username")
    public String findUsername(@RequestParam String fullName,
                               @RequestParam String userEmail,
                               RedirectAttributes ra) {  // POST 처리
        try {
            userService.processFindUsername(fullName, userEmail);
            ra.addFlashAttribute("message", "아이디가 이메일로 전송되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users/find-username";
    }

    /* ─────────────── 비밀번호 재설정 링크 발송 ─────────────── */

    @GetMapping("/find-password")
    public String showFindPassword() {          // GET  폼
        return "find-password";
    }

    @PostMapping("/find-password")
    public String findPassword(@RequestParam String username,
                               @RequestParam String userEmail,
                               RedirectAttributes ra) {  // POST 처리
        try {
            userService.processResetPasswordRequest(username, userEmail);
            ra.addFlashAttribute("message", "재설정 링크가 이메일로 전송되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users/find-password";
    }

    /* ─────────────── 토큰 클릭 ⇒ 새 비밀번호 입력 ─────────────── */

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model m) {
        if (userService.validateResetToken(token) == null) {
            return "redirect:/login?error=invalid_token";
        }
        m.addAttribute("token", token);         // hidden 으로 전달
        return "reset-password";
    }

    /* ─────────────── 새 비밀번호 저장 ─────────────── */

    @PostMapping("/reset-password")
    public String doResetPassword(@RequestParam String token,
                                  @RequestParam String newPassword,
                                  RedirectAttributes ra) {
        try {
            userService.resetPassword(token, newPassword);
            ra.addFlashAttribute("resetSuccess", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/login";
    }
}
