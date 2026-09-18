package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryPrivatePhotoRef;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryElementMapper;
import com.example.travlediary.repository.diary.DiaryMapper;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사진 한 장을 내보내기 전에 무엇을 확인하는지.
 *
 * <p>없는 번호, 남의 것, 부모 관계 불일치는 SQL 조건에서 이미 걸러져 결과가 없다.
 * 그 "결과 없음"이 밖에서 모두 같은 404 로 보이는지, 그리고 잠긴 다이어리만 403 인지를 본다.
 * (PIN 은 공유 수단이 아니라 소유자 본인에 대한 2차 잠금이라 화면과 같은 규칙을 그대로 쓴다)
 */
@ExtendWith(MockitoExtension.class)
class DiaryPrivatePhotoServiceImplTest {

    private static final String KEY =
            "/uploads/diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg";

    @Mock private DiaryMapper diaryMapper;
    @Mock private DiaryElementMapper elementMapper;
    @Mock private DiaryCoverElementMapper coverElementMapper;
    @Mock private DiaryCoverDesignElementMapper designElementMapper;
    @Mock private DiaryPrivatePhotoStorage photoStorage;

    private DiaryPinSession pinSession;
    private DiaryPrivatePhotoServiceImpl service;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        pinSession = new DiaryPinSession();
        service = new DiaryPrivatePhotoServiceImpl(diaryMapper, elementMapper,
                coverElementMapper, designElementMapper,
                new DiaryPinGuard(pinSession), photoStorage);

        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 본인 사진은 저장 키로 바뀌어 저장소까지 간다. */
    @Test
    void anOwnedPagePhotoIsResolvedFromTheStorage() {
        when(elementMapper.findPhotoRef(10L, 1L, 101L, 7L)).thenReturn(ref(KEY, 10L, null));
        DiaryPrivatePhotoStorage.StoredPhotoFile file = file();
        when(photoStorage.resolveForRead(KEY)).thenReturn(file);

        assertThat(service.getPageElementPhoto(10L, 1L, 101L, 7L)).isSameAs(file);
    }

    /**
     * 없는 번호 · 남의 것 · 부모 관계 불일치는 모두 결과가 없고, 밖에서는 같은 404 다.
     * (mapper 조건이 이미 소유자와 부모를 함께 보므로 서비스는 구분하지 않는다)
     */
    @Test
    void everyKindOfMissOrOwnershipMismatchLooksTheSameFromOutside() {
        when(elementMapper.findPhotoRef(10L, 1L, 999L, 7L)).thenReturn(null);
        when(coverElementMapper.findPhotoRef(10L, 999L, 7L)).thenReturn(null);
        when(diaryMapper.findCoverPhotoRef(999L, 7L)).thenReturn(null);
        when(designElementMapper.findPhotoStorageKey(5L, 999L, 7L)).thenReturn(null);

        assertNotFound(() -> service.getPageElementPhoto(10L, 1L, 999L, 7L));
        assertNotFound(() -> service.getCoverElementPhoto(10L, 999L, 7L));
        assertNotFound(() -> service.getCoverImage(999L, 7L));
        assertNotFound(() -> service.getCoverDesignElementPhoto(5L, 999L, 7L));
        // 파일을 열어 보지도 않는다.
        verify(photoStorage, never()).resolveForRead(anyString());
    }

    /** 저장소가 내보낼 수 없다고 하면 그 이유도 밖에서는 404 다. */
    @Test
    void aFileTheStorageRefusesIsAlsoJustNotFound() {
        when(elementMapper.findPhotoRef(10L, 1L, 101L, 7L)).thenReturn(ref(KEY, 10L, null));
        when(photoStorage.resolveForRead(KEY))
                .thenThrow(new IllegalArgumentException("사진 파일을 찾을 수 없습니다."));

        assertNotFound(() -> service.getPageElementPhoto(10L, 1L, 101L, 7L));
    }

    /** 로그인하지 않았으면 조회조차 하지 않는다. */
    @Test
    void anAnonymousRequestNeverReachesTheDatabase() {
        assertThatThrownBy(() -> service.getPageElementPhoto(10L, 1L, 101L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(photoStorage, never()).resolveForRead(anyString());
    }

    /** 잠긴 다이어리의 사진은 본인이어도 풀기 전에는 403 이다. */
    @Test
    void aLockedDiaryPhotoIsForbiddenUntilThePinIsEntered() {
        when(elementMapper.findPhotoRef(10L, 1L, 101L, 7L))
                .thenReturn(ref(KEY, 10L, "{bcrypt}hash"));

        assertThatThrownBy(() -> service.getPageElementPhoto(10L, 1L, 101L, 7L))
                .isInstanceOf(DiaryPinLockedException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(photoStorage, never()).resolveForRead(anyString());
    }

    /** 이 세션에서 풀었으면 그대로 보인다. */
    @Test
    void aLockedDiaryPhotoIsServedOnceTheSessionUnlockedThatDiary() {
        pinSession.unlock(session, 10L);
        when(elementMapper.findPhotoRef(10L, 1L, 101L, 7L))
                .thenReturn(ref(KEY, 10L, "{bcrypt}hash"));
        DiaryPrivatePhotoStorage.StoredPhotoFile file = file();
        when(photoStorage.resolveForRead(KEY)).thenReturn(file);

        assertThat(service.getPageElementPhoto(10L, 1L, 101L, 7L)).isSameAs(file);
    }

    /** 다른 다이어리를 푼 것은 이 다이어리의 잠금과 상관없다. */
    @Test
    void unlockingAnotherDiaryDoesNotOpenThisOne() {
        pinSession.unlock(session, 11L);
        when(diaryMapper.findCoverPhotoRef(10L, 7L)).thenReturn(ref(KEY, 10L, "{bcrypt}hash"));

        assertThatThrownBy(() -> service.getCoverImage(10L, 7L))
                .isInstanceOf(DiaryPinLockedException.class);
    }

    /**
     * 적용된 표지 사진도 다이어리에 속하므로 같은 잠금을 따른다.
     * (라이브러리에서 받은 사진은 애초에 mapper 조건에서 빠져 이 문으로 오지 않는다)
     */
    @Test
    void anAppliedCoverPhotoFollowsTheSameLock() {
        when(coverElementMapper.findPhotoRef(10L, 55L, 7L))
                .thenReturn(ref(KEY, 10L, "{bcrypt}hash"));

        assertThatThrownBy(() -> service.getCoverElementPhoto(10L, 55L, 7L))
                .isInstanceOf(DiaryPinLockedException.class);
    }

    /** 표지 디자인은 다이어리에 속하지 않으므로 PIN 과 무관하게 소유자에게 열린다. */
    @Test
    void aCoverDesignPhotoIsNotAffectedByAnyDiaryPin() {
        String designKey = "/uploads/diary-cover-designs/123e4567-e89b-12d3-a456-426614174000.jpg";
        when(designElementMapper.findPhotoStorageKey(5L, 101L, 7L)).thenReturn(designKey);
        DiaryPrivatePhotoStorage.StoredPhotoFile file = file();
        when(photoStorage.resolveForRead(designKey)).thenReturn(file);

        assertThat(service.getCoverDesignElementPhoto(5L, 101L, 7L)).isSameAs(file);
    }

    /* ===== 도우미 ===== */

    private DiaryPrivatePhotoRef ref(String imageUrl, Long diaryId, String pinHash) {
        DiaryPrivatePhotoRef found = new DiaryPrivatePhotoRef();
        found.setImageUrl(imageUrl);
        found.setDiaryId(diaryId);
        found.setPinHash(pinHash);
        return found;
    }

    private DiaryPrivatePhotoStorage.StoredPhotoFile file() {
        return new DiaryPrivatePhotoStorage.StoredPhotoFile(
                Path.of("/tmp/private/photo.jpg"), "image/jpeg", 12L);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
