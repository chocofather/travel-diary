package com.example.travlediary.service.diary;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 잠긴 다이어리에 닿았을 때 나는 예외.
 *
 * <p>소유권은 맞지만 이 세션에서 아직 PIN 을 풀지 않은 경우다.
 * 그래서 "없다"(404)도 "권한 없음"도 아니고, <b>풀면 볼 수 있다</b>는 뜻이다.
 *
 * <p>일반 요청(POST/AJAX)에는 그대로 403 이 나가고,
 * 주소창으로 연 HTML 화면만 목록의 PIN 입력으로 안내한다.
 * (그 갈림은 {@code DiaryPinLockedAdvice} 가 맡는다)
 */
@Getter
public class DiaryPinLockedException extends ResponseStatusException {

    static final String MESSAGE = "PIN 잠금이 설정된 여행일기입니다.";

    /** 어느 다이어리를 풀어야 하는지. 안내에 쓸 번호 하나뿐이다. */
    private final Long diaryId;

    public DiaryPinLockedException(Long diaryId) {
        super(HttpStatus.FORBIDDEN, MESSAGE);
        this.diaryId = diaryId;
    }
}
