package com.example.travlediary.service.notice;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.NoticeTranslation;
import com.example.travlediary.repository.notice.NoticeMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 공개 공지사항의 언어 대체.
 *
 * <p>대체는 필드마다 따로, <b>요청 언어 → 한국어 원문</b> 한 단계까지만 한다.
 * 요청 언어 번역이 없다고 다른 언어 번역을 가져오지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class NoticePublicLocalizationTest {

    private static final String BASE_TITLE = "서비스 점검 안내";
    private static final String BASE_CONTENT = "<p>한국어 본문</p>";

    @Mock
    private NoticeMapper noticeMapper;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeMapper, new PostContentSanitizer(),
                new NoticeLocalizationService(noticeMapper));
    }

    @Test
    void koreanRequestKeepsTheBaseTextAndNeverReadsTranslations() {
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.KOREAN);

        assertThat(detail.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(detail.getContent()).isEqualTo(BASE_CONTENT);
        verify(noticeMapper, never()).findTranslationsByNoticeId(anyLong());
    }

    @Test
    void englishRequestUsesBothTranslatedFields() {
        givenTranslations(translation("en", "Service maintenance", "<p>English body</p>"));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Service maintenance");
        assertThat(detail.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void onlyTranslatedTitleLeavesTheKoreanBody() {
        givenTranslations(translation("en", "Service maintenance", null));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Service maintenance");
        assertThat(detail.getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void onlyTranslatedBodyLeavesTheKoreanTitle() {
        givenTranslations(translation("en", "   ", "<p>English body</p>"));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(detail.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void missingTranslationRowFallsBackToKorean() {
        givenTranslations();
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(detail.getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void anotherLanguageIsNeverUsedAsAFallback() {
        givenTranslations(translation("ja", "サービス点検のご案内", "<p>日本語本文</p>"));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(detail.getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void emptyQuillTranslationFallsBackToTheKoreanBody() {
        givenTranslations(translation("en", "Service maintenance", "<p><br></p>"));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Service maintenance");
        assertThat(detail.getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void translatedBodyIsSanitizedBeforeItReachesTheScreen() {
        givenTranslations(translation("en", "Service maintenance",
                "<p onclick=\"alert(1)\"><strong>Body</strong></p><script>alert(1)</script>"));
        NoticeDetailDto detail = detail();

        noticeService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getContent())
                .contains("<strong>Body</strong>")
                .doesNotContain("onclick", "script");
    }

    /**
     * 최종 표시 본문은 sanitize 를 정확히 한 번만 거친다.
     * 한국어 원문은 상세 조회에서 이미 거쳤으므로 언어 대체 단계에서 다시 정제하지 않는다.
     */
    @Test
    void displayedContentIsSanitizedExactlyOnce() {
        PostContentSanitizer sanitizer = spy(new PostContentSanitizer());
        NoticeService service = new NoticeService(noticeMapper, sanitizer,
                new NoticeLocalizationService(noticeMapper));
        String storedContent = "<p>한국어 본문</p>";
        when(noticeMapper.incrementPublicViews(10L)).thenReturn(1);
        when(noticeMapper.findPublicDetailById(10L)).thenAnswer(invocation -> {
            NoticeDetailDto stored = detail();
            stored.setContent(storedContent);
            return stored;
        });

        // 한국어 요청: 원문 한 번만 정제한다
        NoticeDetailDto korean = service.getPublicDetail(10L);
        service.localizePublicDetail(korean, SupportedLanguage.KOREAN);
        assertThat(korean.getContent()).isEqualTo(storedContent);
        verify(sanitizer, times(1)).sanitize(storedContent);

        // 영어 요청: 표시할 번역 본문을 한 번만 정제한다
        String translated = "<p onclick=\"alert(1)\">English body</p>";
        givenTranslations(translation("en", "Service maintenance", translated));
        NoticeDetailDto english = service.getPublicDetail(10L);
        service.localizePublicDetail(english, SupportedLanguage.ENGLISH);
        assertThat(english.getContent()).isEqualTo("<p>English body</p>");
        verify(sanitizer, times(1)).sanitize(translated);
        // 원문은 표시값이 아니지만 상세 조회 단계에서 거친 한 번만 유지된다
        verify(sanitizer, times(2)).sanitize(storedContent);
    }

    @Test
    void listReadsEveryTranslationInOneQueryAndFallsBackPerNotice() {
        when(noticeMapper.findTranslationsByNoticeIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                listTranslation(1L, "en", "First notice"),
                // 요청 언어가 아닌 줄과 빈 제목은 쓰지 않는다
                listTranslation(2L, "ja", "二番目のお知らせ"),
                listTranslation(3L, "en", "   ")));
        List<NoticeListItemDto> notices = List.of(item(1L, "첫 번째 공지"),
                item(2L, "두 번째 공지"), item(3L, "세 번째 공지"));

        noticeService.localizePublicList(notices, SupportedLanguage.ENGLISH);

        assertThat(notices)
                .extracting(NoticeListItemDto::getId, NoticeListItemDto::getTitle)
                .containsExactly(tuple(1L, "First notice"), tuple(2L, "두 번째 공지"),
                        tuple(3L, "세 번째 공지"));
        // 공지마다 한 번씩 읽지 않는다
        verify(noticeMapper, times(1)).findTranslationsByNoticeIds(any());
        verify(noticeMapper, never()).findTranslationsByNoticeId(anyLong());
    }

    @Test
    void koreanListRequestKeepsBaseTitlesWithoutAnyTranslationQuery() {
        List<NoticeListItemDto> notices = List.of(item(1L, "첫 번째 공지"));

        noticeService.localizePublicList(notices, SupportedLanguage.KOREAN);

        assertThat(notices).extracting(NoticeListItemDto::getTitle).containsExactly("첫 번째 공지");
        verifyNoMoreInteractions(noticeMapper);
    }

    private void givenTranslations(NoticeTranslation... translations) {
        when(noticeMapper.findTranslationsByNoticeId(10L)).thenReturn(List.of(translations));
    }

    private NoticeDetailDto detail() {
        NoticeDetailDto detail = new NoticeDetailDto();
        detail.setId(10L);
        detail.setTitle(BASE_TITLE);
        detail.setContent(BASE_CONTENT);
        return detail;
    }

    private NoticeListItemDto item(Long id, String title) {
        NoticeListItemDto item = new NoticeListItemDto();
        item.setId(id);
        item.setTitle(title);
        return item;
    }

    private NoticeTranslation translation(String languageCode, String title, String content) {
        return listTranslation(10L, languageCode, title, content);
    }

    private NoticeTranslation listTranslation(Long noticeId, String languageCode, String title) {
        return listTranslation(noticeId, languageCode, title, "<p>body</p>");
    }

    private NoticeTranslation listTranslation(Long noticeId, String languageCode,
                                              String title, String content) {
        NoticeTranslation translation = new NoticeTranslation();
        translation.setNoticeId(noticeId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }
}
