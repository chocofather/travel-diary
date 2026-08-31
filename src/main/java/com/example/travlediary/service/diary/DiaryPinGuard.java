package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * PIN 잠금이 걸린 다이어리를 막는 자리.
 *
 * <p>잠긴 다이어리는 상세 화면만 가려서는 소용이 없다. 주소를 직접 치거나 POST 를 그대로 부르면
 * 페이지·꾸미기 요소·삭제까지 지나가기 때문이다. 그래서 화면마다 검사를 심는 대신,
 * 모든 다이어리 작업이 반드시 지나는 소유권 확인({@code DiaryService.getMyDiary}) 한 곳에서 본다.
 * 새 경로가 늘어도 소유권을 확인하는 한 함께 막힌다.
 *
 * <p>확인 순서는 늘 같다 — 소유권이 먼저이고 PIN 잠금이 그다음이다.
 * PIN 은 로그인이나 소유권을 대신하지 않는다.
 *
 * <p>PIN 을 걸고 풀고 바꾸는 길({@link DiaryPinService})은 이 검사를 지나지 않는다.
 * 잠겨 있어도 풀 수 있어야 하기 때문이다. 그쪽은 소유권과 지금 PIN 을 직접 확인한다.
 */
@Component
@RequiredArgsConstructor
public class DiaryPinGuard {

    private final DiaryPinSession diaryPinSession;

    /**
     * 잠긴 다이어리면 막는다. 잠금이 없거나 이 세션에서 이미 풀었으면 그냥 지나간다.
     *
     * <p>소유권은 부르는 쪽이 이미 확인했다. 여기서는 잠금만 본다.
     */
    public void requireUnlocked(Diary diary) {
        if (diary == null || !diary.isPinEnabled()) {
            return;
        }
        /*
          웹 요청이 아니면(배치·초기화 등) 볼 세션이 없다. 그때는 검사할 것도 없다.
          요청 안에서는 세션이 없는 것도 "아직 풀지 않음"으로 본다.
        */
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpSession session = attributes.getRequest().getSession(false);
        if (diaryPinSession.isUnlocked(session, diary.getId())) {
            return;
        }
        throw new DiaryPinLockedException(diary.getId());
    }
}
