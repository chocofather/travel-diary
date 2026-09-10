package com.example.travlediary.controller.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.service.user.UserService;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.NicknameVocabulary;
import com.example.travlediary.service.user.PasswordPolicy;
import com.example.travlediary.service.user.RegistrationValidationException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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

    /**
     * ✅ 닉네임 자동 추천 API.
     * 형용사 + 동물 + 숫자 구조와 추천 풀(10 x 10 x 1000)은 그대로 두고,
     * 단어만 요청 시점의 사이트 언어로 고른다.
     * <p>
     * 중복 확인은 언어별 문자열이 아니라 canonical 조합 단위로 한다.
     * 같은 조합의 5개 언어 표기 중 하나라도 쓰이고 있으면 그 조합은 버리고 다시 뽑는다.
     * (예: `귀여운토끼123` 이 있으면 `CuteRabbit123` 도 추천하지 않는다)
     * <p>
     * 여기서 만든 문자열은 그대로 저장되는 실제 닉네임이므로 이후 언어를 바꿔도 번역하지 않는다.
     */
    @GetMapping("/generate-nickname")
    public String generateNickname() {
        SupportedLanguage language = SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
        List<NicknameVocabulary.Adjective> adjectives = NicknameVocabulary.ADJECTIVES;
        List<NicknameVocabulary.Animal> animals = NicknameVocabulary.ANIMALS;

        Random random = new Random();
        NicknameVocabulary.Combination combination;
        do {
            combination = new NicknameVocabulary.Combination(
                    adjectives.get(random.nextInt(adjectives.size())),
                    animals.get(random.nextInt(animals.size())),
                    random.nextInt(1000)); // 0~999 랜덤 숫자
            // 5개 언어 표기를 한 번의 조회로 확인한다.
        } while (userService.isAnyNicknameExists(combination.allDisplayNames()));

        return combination.displayIn(language);
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
