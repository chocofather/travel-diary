package com.example.travlediary.controller.diary;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryPinService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 개인 여행일기의 4자리 PIN 잠금.
 *
 * <p>잠긴 다이어리라도 이 길은 지나갈 수 있다. (막으면 풀 방법이 없어진다)
 * 대신 모든 요청이 소유권을 먼저 확인하고, 바꾸거나 없앨 때는 지금 PIN 까지 확인한다.
 *
 * <p>어떤 응답에도 해시나 PIN 값을 싣지 않는다. 돌려주는 것은 성공 여부와 안내 문구뿐이다.
 * 화면 이동 없이 값만 돌려주므로 다음 단계의 PIN 모달이 그대로 받아 쓸 수 있다.
 */
@Controller
@RequestMapping("/diaries/{diaryId:\\d+}/pin")
@RequiredArgsConstructor
public class DiaryPinController {

    private final DiaryPinService diaryPinService;

    /** PIN 을 처음 건다. 이미 걸려 있으면 막힌다. (바꾸려면 아래 change 를 쓴다) */
    @PostMapping
    @ResponseBody
    public ResponseEntity<?> setPin(@PathVariable Long diaryId,
                                    @RequestParam(name = "newPin", required = false) String newPin,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    HttpSession session) {
        try {
            diaryPinService.setPin(diaryId, userDetails.getId(), newPin, session);
        } catch (ResponseStatusException exception) {
            return pinErrorResponse(exception, "PIN을 설정하지 못했습니다.");
        }
        return ResponseEntity.ok(Map.of("pinEnabled", true, "unlocked", true));
    }

    /**
     * PIN 을 확인하고 맞으면 이 세션에서 그 다이어리만 연다.
     * 틀렸을 때는 왜 틀렸는지 나누어 알려 주지 않는다.
     */
    @PostMapping("/unlock")
    @ResponseBody
    public ResponseEntity<?> unlock(@PathVariable Long diaryId,
                                    @RequestParam(name = "pin", required = false) String pin,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    HttpSession session) {
        boolean unlocked;
        try {
            unlocked = diaryPinService.verifyAndUnlock(diaryId, userDetails.getId(), pin, session);
        } catch (ResponseStatusException exception) {
            return pinErrorResponse(exception, "PIN을 확인하지 못했습니다.");
        }
        if (!unlocked) {
            return ResponseEntity.badRequest()
                    .body(Map.of("unlocked", false, "message", "PIN 번호가 올바르지 않습니다."));
        }
        return ResponseEntity.ok(Map.of("unlocked", true));
    }

    /** PIN 을 바꾼다. 지금 PIN 이 맞아야 한다. */
    @PostMapping("/change")
    @ResponseBody
    public ResponseEntity<?> changePin(@PathVariable Long diaryId,
                                       @RequestParam(name = "currentPin", required = false)
                                       String currentPin,
                                       @RequestParam(name = "newPin", required = false)
                                       String newPin,
                                       @AuthenticationPrincipal CustomUserDetails userDetails,
                                       HttpSession session) {
        try {
            diaryPinService.changePin(diaryId, userDetails.getId(), currentPin, newPin, session);
        } catch (ResponseStatusException exception) {
            return pinErrorResponse(exception, "PIN을 변경하지 못했습니다.");
        }
        return ResponseEntity.ok(Map.of("pinEnabled", true, "unlocked", true));
    }

    /** PIN 잠금을 아주 없앤다. 지금 PIN 이 맞아야 한다. (한 번 여는 것과 다르다) */
    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<?> removePin(@PathVariable Long diaryId,
                                       @RequestParam(name = "currentPin", required = false)
                                       String currentPin,
                                       @AuthenticationPrincipal CustomUserDetails userDetails,
                                       HttpSession session) {
        try {
            diaryPinService.removePin(diaryId, userDetails.getId(), currentPin, session);
        } catch (ResponseStatusException exception) {
            return pinErrorResponse(exception, "PIN 잠금을 해제하지 못했습니다.");
        }
        return ResponseEntity.ok(Map.of("pinEnabled", false));
    }

    /**
     * PIN 요청의 오류 응답.
     * 없는 다이어리/남의 다이어리는 그대로 다시 던져 기존 404 정책을 지킨다.
     * (그 밖의 4xx 는 이유를 그대로 알려 준다 — 내부 값은 담기지 않는다)
     */
    private ResponseEntity<?> pinErrorResponse(ResponseStatusException exception,
                                               String defaultMessage) {
        if (!exception.getStatusCode().is4xxClientError()
                || HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            throw exception;
        }
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null
                        ? defaultMessage : exception.getReason()));
    }
}
