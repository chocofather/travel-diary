package com.example.travlediary.controller.user;

import com.example.travlediary.service.user.UserService;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.PasswordPolicy;
import com.example.travlediary.service.user.RegistrationValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/users")
public class UserApiController {
    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    // 아이디 중복 검사 API (JSON 응답)
    @GetMapping("/check-username")
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", userService.isUsernameExists(username));
        return response;
    }

    // 닉네임 중복 검사 API (AJAX 요청 처리)
    @GetMapping("/check-nickname")
    public Map<String, Object> checkNickname(@RequestParam String nickname) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean exists = userService.isNicknameExists(nickname);
            response.put("exists", exists);
            response.put("status", exists ? "DUPLICATE" : "AVAILABLE");
        } catch (NicknamePolicy.ViolationException exception) {
            response.put("exists", true);
            response.put("status", exception.getViolationType().name());
        }
        return response;
    }

    // ✅ 닉네임 자동 추천 API
    @GetMapping("/generate-nickname")
    public String generateNickname() {
        String[] adjectives = {"귀여운", "상냥한", "멋진", "빠른", "행복한", "용감한", "차가운", "따뜻한", "강한", "조용한"};
        String[] nouns = {"고양이", "강아지", "토끼", "호랑이", "사자", "부엉이", "여우", "늑대", "펭귄", "곰"};

        Random random = new Random();
        String nickname;
        do {
            String adjective = adjectives[random.nextInt(adjectives.length)];
            String noun = nouns[random.nextInt(nouns.length)];
            int number = random.nextInt(1000); // 0~999 랜덤 숫자
            nickname = adjective + noun + number;
        } while (userService.isNicknameExists(nickname)); // 중복 체크 후 중복되면 다시 생성

        return nickname;
    }

    @PostMapping("/validate-password")
    public ResponseEntity<?> validatePassword(@RequestParam String password) {
        if (!PasswordPolicy.isValid(password)) {
            return ResponseEntity.badRequest().body(PasswordPolicy.INVALID_MESSAGE);
        }
        return ResponseEntity.ok("사용 가능한 비밀번호입니다.");
    }

    /*이메일 중복 */
    @GetMapping("/check-email")
    public Map<String, Object> checkEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("exists", userService.isEmailExists(email));
            response.put("valid", true);
        } catch (RegistrationValidationException exception) {
            response.put("exists", false);
            response.put("valid", false);
        }
        return response;
    }

}
