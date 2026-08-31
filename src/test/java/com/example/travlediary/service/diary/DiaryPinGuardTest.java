package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PIN 잠금 검사.
 *
 * <p>다이어리 한 권을 다루는 모든 길(읽기·수정·삭제, 페이지, 꾸미기 요소, 표지)이
 * 소유권 확인을 위해 DiaryService.getMyDiary 를 지난다. 그래서 그 한 곳만 막으면
 * 주소를 직접 치거나 POST 를 그대로 불러도 잠금을 우회할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryPinGuardTest {

    @Mock
    private DiaryMapper diaryMapper;

    private DiaryPinSession diaryPinSession;
    private DiaryService diaryService;
    private MockHttpSession session;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        diaryPinSession = new DiaryPinSession();
        diaryService = new DiaryServiceImpl(diaryMapper, new DiaryPinGuard(diaryPinSession));
        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 잠금이 없는 다이어리는 예전 그대로다. */
    @Test
    void aDiaryWithoutAPinIsReadTheSameWayAsBefore() {
        givenDiary(10L, 7L, null);

        assertThatCode(() -> diaryService.getMyDiary(10L, 7L)).doesNotThrowAnyException();
    }

    /**
     * 잠긴 다이어리는 세션에서 풀기 전까지 막힌다.
     * 소유자 본인이어도 마찬가지다 — PIN 은 소유권 위에 한 겹 더 거는 잠금이다.
     */
    @Test
    void aLockedDiaryIsBlockedUntilThisSessionUnlocksIt() {
        givenDiary(10L, 7L, "$2a$10$hash");

        assertThatThrownBy(() -> diaryService.getMyDiary(10L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 잠금이 설정된 여행일기입니다.")
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        diaryPinSession.unlock(session, 10L);
        assertThatCode(() -> diaryService.getMyDiary(10L, 7L)).doesNotThrowAnyException();
    }

    /** 한 권을 풀었다고 다른 잠긴 다이어리까지 열리지 않는다. */
    @Test
    void unlockingOneDiaryDoesNotOpenAnother() {
        givenDiary(10L, 7L, "$2a$10$hash");
        givenDiary(11L, 7L, "$2a$10$hash");
        diaryPinSession.unlock(session, 10L);

        assertThatCode(() -> diaryService.getMyDiary(10L, 7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> diaryService.getMyDiary(11L, 7L))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** 세션이 없는 요청은 아직 풀지 않은 것으로 본다. (로그아웃 뒤에도 다시 물어본다) */
    @Test
    void aRequestWithoutASessionIsTreatedAsLocked() {
        givenDiary(10L, 7L, "$2a$10$hash");
        request.setSession(null);

        assertThatThrownBy(() -> diaryService.getMyDiary(10L, 7L))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** 소유권이 먼저다. 남의 다이어리는 잠금 여부와 상관없이 404 다. */
    @Test
    void ownershipIsCheckedBeforeTheLock() {
        when(diaryMapper.findByIdAndUserId(10L, 8L)).thenReturn(null);

        assertThatThrownBy(() -> diaryService.getMyDiary(10L, 8L))
                .isInstanceOf(ResponseStatusException.class)
                // 잠금 안내가 아니라 기존 404 정책 그대로다
                .hasMessageContaining("다이어리를 찾을 수 없습니다.")
                .hasMessageNotContaining("PIN");
    }

    /** 잠금 여부는 해시의 유무로만 판단한다. */
    @Test
    void theLockIsDecidedByThePresenceOfTheHashAlone() {
        Diary diary = new Diary();
        assertThat(diary.isPinEnabled()).isFalse();
        diary.setPinHash("  ");
        assertThat(diary.isPinEnabled()).isFalse();
        diary.setPinHash("$2a$10$hash");
        assertThat(diary.isPinEnabled()).isTrue();
    }

    private void givenDiary(Long diaryId, Long userId, String pinHash) {
        Diary diary = new Diary();
        diary.setId(diaryId);
        diary.setUserId(userId);
        diary.setPinHash(pinHash);
        org.mockito.Mockito.lenient()
                .when(diaryMapper.findByIdAndUserId(diaryId, userId)).thenReturn(diary);
    }
}
