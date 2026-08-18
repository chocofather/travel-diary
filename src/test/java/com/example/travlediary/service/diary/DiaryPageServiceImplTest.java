package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryPageMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryPageServiceImplTest {

    @Mock
    private DiaryService diaryService;
    @Mock
    private DiaryPageMapper diaryPageMapper;

    private DiaryPageService diaryPageService;

    @BeforeEach
    void setUp() {
        // 정리(sanitize)까지 실제 동작을 확인하기 위해 실제 구현을 쓴다.
        diaryPageService = new DiaryPageServiceImpl(diaryService, diaryPageMapper,
                new DiaryContentSanitizer(new PostContentSanitizer()));
    }

    @Test
    void contentSaveTouchesOnlyTheContentColumn() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(page());
        when(diaryPageMapper.updateContent(eq(3L), eq(10L), any())).thenReturn(1);

        diaryPageService.updateContent(10L, 3L, 7L, "<p>오늘의 기록</p>");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diaryPageMapper).updateContent(eq(3L), eq(10L), captor.capture());
        assertThat(captor.getValue()).contains("오늘의 기록");
        // 날짜/순서/배경을 바꾸는 UPDATE 는 실행되지 않는다
        verify(diaryPageMapper, never()).update(any(DiaryPage.class));
    }

    @Test
    void contentSaveDropsScriptsAndKeepsAllowedFormatting() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(page());
        when(diaryPageMapper.updateContent(eq(3L), eq(10L), any())).thenReturn(1);

        diaryPageService.updateContent(10L, 3L, 7L,
                "<p class=\"ql-align-center\"><strong>제주</strong>"
                        + "<span class=\"ql-size-large ql-font-mitmi\" style=\"color: #ff0000;\">여행</span>"
                        + "<em>기록</em></p>"
                        + "<script>alert(1)</script>"
                        + "<p onclick=\"steal()\" data-x=\"1\">두 번째 줄</p>"
                        + "<p><img src=\"javascript:alert(1)\" /><a href=\"javascript:alert(1)\">링크</a></p>");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diaryPageMapper).updateContent(eq(3L), eq(10L), captor.capture());
        String saved = captor.getValue();

        assertThat(saved).doesNotContain("script", "onclick", "data-x", "javascript:", "<a", "<img");
        assertThat(saved).contains("<strong>제주</strong>");
        assertThat(saved).contains("<em>기록</em>");
        assertThat(saved).contains("ql-align-center");
        assertThat(saved).contains("ql-size-large");
        assertThat(saved).contains("ql-font-mitmi");
        assertThat(saved).contains("color: #ff0000");
        // 허용하지 않은 태그는 글자만 남는다
        assertThat(saved).contains("두 번째 줄");
    }

    @Test
    void diaryFontClassesSurviveSanitizeButUnknownOnesDoNot() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(page());
        when(diaryPageMapper.updateContent(eq(3L), eq(10L), any())).thenReturn(1);

        diaryPageService.updateContent(10L, 3L, 7L,
                "<p><span class=\"ql-font-park-dahyun\">박다현체</span>"
                        + "<span class=\"ql-font-dunggeunmo\">둥근모꼴</span>"
                        + "<span class=\"ql-font-chosun-gungsuh ql-size-large\">조선궁서체</span>"
                        + "<span class=\"ql-font-pretendard\">이제 다이어리 목록에 없는 글꼴</span>"
                        + "<span class=\"ql-font-evil\" onclick=\"steal()\">임의 클래스</span></p>");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diaryPageMapper).updateContent(eq(3L), eq(10L), captor.capture());
        String saved = captor.getValue();

        assertThat(saved).contains("ql-font-park-dahyun");
        assertThat(saved).contains("ql-font-dunggeunmo");
        assertThat(saved).contains("ql-font-chosun-gungsuh");
        assertThat(saved).contains("ql-size-large");
        // 다이어리 목록에 없는 글꼴과 임의 클래스, 이벤트 핸들러는 남지 않는다
        assertThat(saved).doesNotContain("ql-font-pretendard");
        assertThat(saved).doesNotContain("ql-font-evil");
        assertThat(saved).doesNotContain("onclick");
        // 글자는 지워지지 않는다
        assertThat(saved).contains("박다현체").contains("임의 클래스");
    }

    @Test
    void emptyEditorContentIsStoredAsNull() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(page());
        when(diaryPageMapper.updateContent(3L, 10L, null)).thenReturn(1);

        diaryPageService.updateContent(10L, 3L, 7L, "<p><br></p>");

        verify(diaryPageMapper).updateContent(3L, 10L, null);
    }

    @Test
    void contentSaveOfAMissingPageIsNotFound() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(99L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> diaryPageService.updateContent(10L, 99L, 7L, "<p>기록</p>"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("페이지를 찾을 수 없습니다.");
        verify(diaryPageMapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void contentSaveOfAnotherUsersDiaryIsBlockedByTheOwnershipCheck() {
        when(diaryService.getMyDiary(10L, 8L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        assertThatThrownBy(() -> diaryPageService.updateContent(10L, 3L, 8L, "<p>기록</p>"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("다이어리를 찾을 수 없습니다.");
        verify(diaryPageMapper, never()).updateContent(any(), any(), any());
    }

    @Test
    void pageSettingsUpdateKeepsTheExistingContent() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage existing = page();
        existing.setContent("<p>지키고 싶은 기록</p>");
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(existing);
        when(diaryPageMapper.update(any(DiaryPage.class))).thenReturn(1);

        DiaryPage changed = new DiaryPage();
        changed.setPageDate(LocalDate.of(2026, 8, 2));
        changed.setPageOrder(1);
        changed.setBackgroundType("LINED");
        diaryPageService.update(10L, 3L, 7L, changed);

        ArgumentCaptor<DiaryPage> captor = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageMapper).update(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("<p>지키고 싶은 기록</p>");
        assertThat(captor.getValue().getBackgroundType()).isEqualTo("LINED");
    }

    @Test
    void deletingAMiddlePageShiftsTheFollowingOrdersForward() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage second = page();
        second.setPageOrder(2);
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(second);
        when(diaryPageMapper.delete(3L, 10L)).thenReturn(1);

        diaryPageService.delete(10L, 3L, 7L);

        // 1,2,3,4 에서 2 를 지우면 3,4 가 2,3 이 된다
        InOrder inOrder = inOrder(diaryPageMapper);
        inOrder.verify(diaryPageMapper).delete(3L, 10L);
        inOrder.verify(diaryPageMapper).shiftPageOrdersAfter(10L, 2);
    }

    @Test
    void deletingTheLastPageStillRunsTheRenumbering() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage last = page();
        last.setPageOrder(4);
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(last);
        when(diaryPageMapper.delete(3L, 10L)).thenReturn(1);

        diaryPageService.delete(10L, 3L, 7L);

        // 뒤에 페이지가 없으면 옮길 행이 없을 뿐, 같은 경로를 그대로 탄다
        verify(diaryPageMapper).shiftPageOrdersAfter(10L, 4);
    }

    @Test
    void deletingTheFirstPageMovesEveryLaterPageForward() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage first = page();
        first.setPageOrder(1);
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(first);
        when(diaryPageMapper.delete(3L, 10L)).thenReturn(1);

        diaryPageService.delete(10L, 3L, 7L);

        verify(diaryPageMapper).shiftPageOrdersAfter(10L, 1);
    }

    @Test
    void failedDeleteDoesNotRenumber() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageMapper.findByIdAndDiaryId(3L, 10L)).thenReturn(page());
        when(diaryPageMapper.delete(3L, 10L)).thenReturn(0);

        assertThatThrownBy(() -> diaryPageService.delete(10L, 3L, 7L))
                .isInstanceOf(ResponseStatusException.class);
        verify(diaryPageMapper, never()).shiftPageOrdersAfter(any(), any());
    }

    @Test
    void deletingAnotherUsersPageIsBlockedBeforeAnyChange() {
        when(diaryService.getMyDiary(10L, 8L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        assertThatThrownBy(() -> diaryPageService.delete(10L, 3L, 8L))
                .isInstanceOf(ResponseStatusException.class);
        verify(diaryPageMapper, never()).delete(any(), any());
        verify(diaryPageMapper, never()).shiftPageOrdersAfter(any(), any());
    }

    @Test
    void deleteAndRenumberingRunInOneTransaction() throws NoSuchMethodException {
        assertThat(DiaryPageServiceImpl.class
                .getDeclaredMethod("delete", Long.class, Long.class, Long.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void newPageAfterRenumberingTakesTheLastOrderPlusOne() {
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        // 재번호화 뒤라 마지막 순서는 3 이다
        when(diaryPageMapper.findMaxPageOrder(10L)).thenReturn(3);
        when(diaryPageMapper.insert(any(DiaryPage.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryPage.class).setId(9L);
            return 1;
        });
        when(diaryPageMapper.findByIdAndDiaryId(9L, 10L)).thenReturn(page());

        DiaryPage added = new DiaryPage();
        added.setPageDate(LocalDate.of(2026, 8, 3));
        added.setBackgroundType("PLAIN");
        diaryPageService.append(10L, 7L, added);

        ArgumentCaptor<DiaryPage> captor = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageMapper).insert(captor.capture());
        assertThat(captor.getValue().getPageOrder()).isEqualTo(4);
    }

    private Diary diary() {
        Diary diary = new Diary();
        diary.setId(10L);
        diary.setUserId(7L);
        diary.setStartDate(LocalDate.of(2026, 8, 1));
        diary.setEndDate(LocalDate.of(2026, 8, 5));
        return diary;
    }

    private DiaryPage page() {
        DiaryPage page = new DiaryPage();
        page.setId(3L);
        page.setDiaryId(10L);
        page.setPageDate(LocalDate.of(2026, 8, 1));
        page.setPageOrder(1);
        page.setBackgroundType("PLAIN");
        return page;
    }
}
