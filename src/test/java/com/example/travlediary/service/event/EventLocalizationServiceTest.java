package com.example.travlediary.service.event;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 이벤트 제목·본문이 필드마다 따로 대체되는지, 포스터는 다른 규칙을 따르는지 본다.
 *
 * <p>제목·본문: 요청 언어 → 한국어 → 남은 언어 → base → null.
 * <p>포스터: 요청 언어 → 한국어 → base → null ('남은 언어' 없음).
 */
@ExtendWith(MockitoExtension.class)
class EventLocalizationServiceTest {

    private static final String BASE_TITLE = "이벤트 원문 제목";
    private static final String BASE_DESCRIPTION = "이벤트 원문 본문";
    private static final String BASE_POSTER = "/uploads/events/posters/base.png";

    @Mock private EventMapper eventMapper;

    private EventLocalizationService localizationService;

    @BeforeEach
    void setUp() {
        localizationService = new EventLocalizationService(eventMapper);
    }

    @Test
    void requestedLanguageRowIsUsedWhenItHasValues() {
        EventTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title", "English body", "/en.png"),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getEventId()).isEqualTo(7L);
        assertThat(localized.getLanguageCode()).isEqualTo("en");
        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getDescription()).isEqualTo("English body");
        assertThat(localized.getPosterImg()).isEqualTo("/en.png");
    }

    @Test
    void koreanRowIsUsedWhenRequestedLanguageRowIsMissing() {
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getDescription()).isEqualTo("한국어 본문");
    }

    @Test
    void deterministicRemainingLanguageIsUsedWhenKoreanIsMissingToo() {
        // language_code ASC 로 en 이 ja 보다 앞이므로 언제 불러도 en 이 나와야 한다.
        EventTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED,
                translation(9L, 7L, "ja", "日本語タイトル", "日本語本文", null),
                translation(3L, 7L, "en", "English title", "English body", null));

        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getDescription()).isEqualTo("English body");
    }

    @Test
    void baseValuesAreUsedWhenNoTranslationRowExists() {
        EventTranslation localized = localize(SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(localized.getDescription()).isEqualTo(BASE_DESCRIPTION);
        assertThat(localized.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void missingValueEverywhereFallsBackToNull() {
        EventTranslation localized = localizationService.resolveLocalizedContent(
                7L, null, null, null, List.of(), SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isNull();
        assertThat(localized.getDescription()).isNull();
        assertThat(localized.getPosterImg()).isNull();
    }

    @Test
    void titleAndDescriptionFallBackIndependently() {
        // en 줄에 제목만 있으면 제목은 en, 본문은 ko 가 되어야 한다. 줄 통째로 고르지 않는다.
        EventTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title", "   ", null),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", null));

        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getDescription()).isEqualTo("한국어 본문");
    }

    @Test
    void blankTranslationValuesAreTreatedAsMissing() {
        EventTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "  ", "", "   "),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getDescription()).isEqualTo("한국어 본문");
        assertThat(localized.getPosterImg()).isEqualTo("/ko.png");
    }

    @Test
    void traditionalChineseDoesNotPreferSimplifiedChineseOverKorean() {
        EventTranslation localized = localize(
                SupportedLanguage.CHINESE_TRADITIONAL,
                translation(4L, 7L, "zh-CN", "简体标题", "简体正文", null),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", null));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getDescription()).isEqualTo("한국어 본문");
    }

    @Test
    void simplifiedChineseDoesNotPreferTraditionalChineseOverKorean() {
        EventTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED,
                translation(5L, 7L, "zh-TW", "繁體標題", "繁體正文", null),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", null));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getDescription()).isEqualTo("한국어 본문");
    }

    @Test
    void posterUsesTheRequestedLanguageImageWhenItExists() {
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(3L, 7L, "ja", "日本語タイトル", "日本語本文", "/ja.png"),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getPosterImg()).isEqualTo("/ja.png");
    }

    @Test
    void posterFallsBackToKoreanWhenTheRequestedLanguageHasNone() {
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(3L, 7L, "ja", "日本語タイトル", "日本語本文", null),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getPosterImg()).isEqualTo("/ko.png");
    }

    @Test
    void posterFallsBackToTheBaseEventImageWhenKoreanHasNone() {
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(3L, 7L, "ja", "日本語タイトル", "日本語本文", null),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", null));

        assertThat(localized.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void posterNeverFallsBackToAnotherForeignLanguageImage() {
        // 일본어 화면에 영어 포스터를 띄우면 읽을 수 없다. 한국어 포스터가 우선이다.
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(4L, 7L, "en", "English title", "English body", "/en.png"),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png"));

        assertThat(localized.getPosterImg()).isEqualTo("/ko.png");
    }

    @Test
    void posterSkipsForeignImagesAndLandsOnTheBaseEventImage() {
        // ko 포스터가 없으면 영어 포스터로 내려가지 않고 base 포스터를 쓴다.
        EventTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(4L, 7L, "en", "English title", "English body", "/en.png"),
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", null));

        assertThat(localized.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void singleLookupReadsTranslationsOnceForTheRequestedEvent() {
        when(eventMapper.findTranslationsByEventId(7L)).thenReturn(List.of(
                translation(2L, 7L, "en", "English title", "English body", "/en.png")));

        EventTranslation localized = localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER, SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo("English title");
        verify(eventMapper, times(1)).findTranslationsByEventId(7L);
        verify(eventMapper, never()).findTranslationsByEventIds(anyList());
    }

    @Test
    void listLocalizationReadsEveryTranslationInASingleQuery() {
        when(eventMapper.findTranslationsByEventIds(List.of(7L, 8L, 9L))).thenReturn(List.of(
                translation(1L, 7L, "ko", "칠번 한국어", "칠번 한국어 본문", "/ko-7.png"),
                translation(2L, 7L, "en", "Seven English", "Seven English body", "/en-7.png"),
                translation(3L, 8L, "ko", "팔번 한국어", "팔번 한국어 본문", null)));

        Map<Long, EventTranslation> localized =
                localizationService.resolveLocalizedContentByEvents(
                        List.of(event(7L, "칠번 원문", "칠번 원문 본문", "/base-7.png"),
                                event(8L, "팔번 원문", "팔번 원문 본문", "/base-8.png"),
                                event(9L, "구번 원문", "구번 원문 본문", "/base-9.png")),
                        SupportedLanguage.ENGLISH);

        assertThat(localized.get(7L).getTitle()).isEqualTo("Seven English");
        assertThat(localized.get(7L).getPosterImg()).isEqualTo("/en-7.png");
        assertThat(localized.get(8L).getTitle()).isEqualTo("팔번 한국어");
        // 팔번은 어느 언어에도 포스터가 없어 base 포스터로 내려간다.
        assertThat(localized.get(8L).getPosterImg()).isEqualTo("/base-8.png");
        // 번역이 하나도 없는 이벤트는 base 로 내려온다.
        assertThat(localized.get(9L).getTitle()).isEqualTo("구번 원문");
        assertThat(localized.get(9L).getDescription()).isEqualTo("구번 원문 본문");
        assertThat(localized.get(9L).getPosterImg()).isEqualTo("/base-9.png");

        verify(eventMapper, times(1)).findTranslationsByEventIds(List.of(7L, 8L, 9L));
        verify(eventMapper, never()).findTranslationsByEventId(anyLong());
    }

    @Test
    void emptyListNeverReadsTranslations() {
        Map<Long, EventTranslation> localized =
                localizationService.resolveLocalizedContentByEvents(
                        List.of(), SupportedLanguage.ENGLISH);

        assertThat(localized).isEmpty();
        verifyNoInteractions(eventMapper);
    }

    @Test
    void localizationNeverMutatesTheRowsOrTheBaseEventItWasGiven() {
        EventTranslation koreanRow =
                translation(1L, 7L, "ko", "한국어 제목", "한국어 본문", "/ko.png");
        Event base = event(7L, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER);

        when(eventMapper.findTranslationsByEventIds(List.of(7L)))
                .thenReturn(List.of(koreanRow));

        EventTranslation localized = localizationService
                .resolveLocalizedContentByEvents(List.of(base), SupportedLanguage.ENGLISH)
                .get(7L);

        assertThat(localized).isNotSameAs(koreanRow);
        assertThat(localized.getTitle()).isEqualTo("한국어 제목");

        assertThat(koreanRow.getTitle()).isEqualTo("한국어 제목");
        assertThat(koreanRow.getDescription()).isEqualTo("한국어 본문");
        assertThat(koreanRow.getPosterImg()).isEqualTo("/ko.png");
        assertThat(koreanRow.getLanguageCode()).isEqualTo("ko");

        // 이벤트 원문과 언어와 무관한 값들은 그대로 남는다.
        assertThat(base.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(base.getDescription()).isEqualTo(BASE_DESCRIPTION);
        assertThat(base.getPosterImg()).isEqualTo(BASE_POSTER);
        assertThat(base.getEventImg()).isEqualTo("/uploads/events/base-main.png");
        assertThat(base.getEventType()).isEqualTo(EventType.INFOGRAPHIC);
        assertThat(base.getSlide()).isTrue();
        assertThat(base.getUserId()).isEqualTo(1L);
        assertThat(base.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(base.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void rowsBelongingToAnotherEventAreIgnored() {
        EventTranslation localized = localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER,
                List.of(translation(2L, 99L, "en", "Other event", "Other body", "/other.png")),
                SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(localized.getDescription()).isEqualTo(BASE_DESCRIPTION);
        assertThat(localized.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    private EventTranslation localize(SupportedLanguage language,
                                      EventTranslation... translations) {
        return localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER,
                List.of(translations), language);
    }

    private EventTranslation translation(Long id, Long eventId, String languageCode,
                                         String title, String description, String posterImg) {
        EventTranslation translation = new EventTranslation();
        translation.setId(id);
        translation.setEventId(eventId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setDescription(description);
        translation.setPosterImg(posterImg);
        return translation;
    }

    private Event event(Long id, String title, String description, String posterImg) {
        Event event = new Event();
        event.setId(id);
        event.setTitle(title);
        event.setDescription(description);
        event.setPosterImg(posterImg);
        event.setEventImg("/uploads/events/base-main.png");
        event.setEventType(EventType.INFOGRAPHIC);
        event.setSlide(true);
        event.setUserId(1L);
        event.setStartDate(LocalDate.of(2026, 8, 1));
        event.setEndDate(LocalDate.of(2026, 8, 31));
        return event;
    }
}
