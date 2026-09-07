package com.example.travlediary.service.notice;

import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.dto.NoticeTranslationForm;
import com.example.travlediary.model.Notice;
import com.example.travlediary.model.NoticeTranslation;
import com.example.travlediary.repository.notice.NoticeMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 공지사항 번역 저장 규칙.
 *
 * <p>한국어는 notices 원문이 갖고, 나머지 언어는 값이 있으면 남고(INSERT/UPDATE)
 * 제목·본문을 모두 비우면 그 언어 줄만 사라진다(DELETE). ko 줄은 만들지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class NoticeTranslationSaveTest {

    @Mock
    private NoticeMapper noticeMapper;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeMapper, new PostContentSanitizer(),
                new NoticeLocalizationService(noticeMapper));
    }

    @Test
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new NoticeForm().getTranslations())
                .extracting(NoticeTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
    }

    @Test
    void koreanOnlyCreateLeavesTheTranslationTableUntouched() {
        givenGeneratedId(10L);

        noticeService.create(form(), 7L);

        verify(noticeMapper).insertNotice(any(Notice.class));
        verify(noticeMapper, never()).insertTranslation(any());
        verify(noticeMapper, never()).updateTranslation(any());
        verify(noticeMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void createStoresOnlyTheFilledLanguagesAndNeverKorean() {
        givenGeneratedId(10L);
        NoticeForm form = form();
        setSlot(form, "ko", "무시되는 제목", "<p>무시되는 본문</p>");
        setSlot(form, "en", "  Service maintenance  ", "<p>Notice body</p>");
        // 제목 없이 본문만 넣은 부분 번역도 저장된다
        setSlot(form, "ja", "   ", "<p>お知らせ</p>");
        // 태그만 남은 빈 Quill 본문은 값이 없는 것으로 본다
        setSlot(form, "zh-CN", "", "<p><br></p>");

        noticeService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(NoticeTranslation::getNoticeId, NoticeTranslation::getLanguageCode,
                        NoticeTranslation::getTitle, NoticeTranslation::getContent)
                .containsExactly(
                        tuple(10L, "en", "Service maintenance", "<p>Notice body</p>"),
                        tuple(10L, "ja", null, "<p>お知らせ</p>"));
        verify(noticeMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void createSanitizesTranslationContentWithTheSamePolicyAsKorean() {
        givenGeneratedId(10L);
        NoticeForm form = form();
        setSlot(form, "en", "Notice",
                "<p onclick=\"alert(1)\"><strong>Body</strong></p><script>alert(1)</script>");

        noticeService.create(form, 7L);

        assertThat(captureInserts()).singleElement()
                .satisfies(translation -> assertThat(translation.getContent())
                        .contains("<strong>Body</strong>")
                        .doesNotContain("onclick", "script"));
    }

    @Test
    void updateInsertsMissingLanguagesUpdatesExistingOnesAndClearsEmptiedOnes() {
        givenNotice(10L);
        when(noticeMapper.findTranslationsByNoticeId(10L)).thenReturn(List.of(
                stored(1L, 10L, "en", "Old title", "<p>Old body</p>"),
                stored(2L, 10L, "zh-CN", "旧标题", "<p>旧正文</p>"),
                stored(3L, 10L, "zh-TW", "舊標題", "<p>舊內容</p>")));

        NoticeForm form = form();
        setSlot(form, "en", "New title", "<p>New body</p>");   // UPDATE
        setSlot(form, "ja", "お知らせ", "<p>本文</p>");            // INSERT
        setSlot(form, "zh-CN", "  ", "<p><br></p>");            // DELETE
        setSlot(form, "zh-TW", "舊標題", "<p>舊內容</p>");        // 그대로 UPDATE

        noticeService.update(10L, form);

        ArgumentCaptor<NoticeTranslation> updated =
                ArgumentCaptor.forClass(NoticeTranslation.class);
        verify(noticeMapper, atLeast(1)).updateTranslation(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(NoticeTranslation::getLanguageCode, NoticeTranslation::getTitle)
                .containsExactly(tuple("en", "New title"), tuple("zh-TW", "舊標題"));

        assertThat(captureInserts())
                .extracting(NoticeTranslation::getLanguageCode, NoticeTranslation::getTitle)
                .containsExactly(tuple("ja", "お知らせ"));

        // 비운 언어 한 줄만 지운다. 다른 언어는 건드리지 않는다.
        verify(noticeMapper).deleteTranslation(10L, "zh-CN");
        verify(noticeMapper, never()).deleteTranslation(10L, "en");
        verify(noticeMapper, never()).deleteTranslation(10L, "ja");
        verify(noticeMapper, never()).deleteTranslation(10L, "zh-TW");
        verify(noticeMapper, never()).deleteTranslation(10L, "ko");
    }

    @Test
    void editFormLoadsStoredTranslationsByLanguageCodeNotByRowOrder() {
        Notice notice = notice(10L);
        when(noticeMapper.findById(10L)).thenReturn(notice);
        // 조회 순서가 뒤섞여 들어와도 언어 코드로 슬롯을 찾는다
        when(noticeMapper.findTranslationsByNoticeId(10L)).thenReturn(List.of(
                stored(3L, 10L, "zh-TW", "繁體標題", "<p>繁體內容</p>"),
                stored(1L, 10L, "en", "English title", "<p>English body</p>")));

        NoticeForm form = noticeService.getForm(10L);

        assertThat(form.getTitle()).isEqualTo("기존 공지");
        assertThat(form.getTranslations())
                .extracting(NoticeTranslationForm::getLanguageCode,
                        NoticeTranslationForm::getTitle, NoticeTranslationForm::getContent)
                .containsExactly(
                        tuple("ko", "", ""),
                        tuple("en", "English title", "<p>English body</p>"),
                        tuple("ja", "", ""),
                        tuple("zh-CN", "", ""),
                        tuple("zh-TW", "繁體標題", "<p>繁體內容</p>"));
    }

    private List<NoticeTranslation> captureInserts() {
        ArgumentCaptor<NoticeTranslation> captor = ArgumentCaptor.forClass(NoticeTranslation.class);
        verify(noticeMapper, org.mockito.Mockito.atLeast(0)).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private void givenGeneratedId(Long generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Notice.class).setId(generatedId);
            return 1;
        }).when(noticeMapper).insertNotice(any(Notice.class));
    }

    private void givenNotice(Long id) {
        Notice notice = notice(id);
        when(noticeMapper.findByIdForUpdate(id)).thenReturn(notice);
        when(noticeMapper.updateNotice(notice)).thenReturn(1);
    }

    private Notice notice(Long id) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setTitle("기존 공지");
        notice.setContent("<p>기존 본문</p>");
        return notice;
    }

    private NoticeTranslation stored(Long id, Long noticeId, String languageCode,
                                     String title, String content) {
        NoticeTranslation translation = new NoticeTranslation();
        translation.setId(id);
        translation.setNoticeId(noticeId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }

    private NoticeForm form() {
        NoticeForm form = new NoticeForm();
        form.setTitle("서비스 점검 안내");
        form.setContent("<p>공지 본문</p>");
        return form;
    }

    private void setSlot(NoticeForm form, String languageCode, String title, String content) {
        form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .forEach(slot -> {
                    slot.setTitle(title);
                    slot.setContent(content);
                });
    }
}
