package com.example.travlediary.controller.event;

import com.example.travlediary.dto.EventSlideDto;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.event.EventLocalizationService;
import com.example.travlediary.service.event.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 메인 홈 슬라이더 응답을 본다.
 *
 * <p>노출 대상과 정렬은 기존 조회가 정하고, 여기서는 제목·설명만 요청 언어로 바뀐다.
 * 대표 이미지는 언어 공용이라 언어를 바꿔도 그대로다.
 */
@ExtendWith(MockitoExtension.class)
class EventApiControllerTest {

    @Mock private EventService eventService;
    @Mock private EventMapper eventMapper;

    private EventApiController controller;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        controller = new EventApiController(
                eventService, new EventLocalizationService(eventMapper));
        originalLocale = LocaleContextHolder.getLocale();
    }

    @AfterEach
    void restoreLocale() {
        LocaleContextHolder.setLocale(originalLocale);
    }

    @Test
    void slideEndpointKeepsReturningActiveSlideEventsFromService() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));

        List<EventSlideDto> slides = controller.slideEvents();

        assertThat(slides).hasSize(1);
        assertThat(slides.get(0).getId()).isEqualTo(10L);
        assertThat(slides.get(0).getTitle()).isEqualTo("메인 슬라이드 이벤트");
        assertThat(slides.get(0).getDescription()).isEqualTo("메인 슬라이드 설명");
        assertThat(slides.get(0).getEventImg()).isEqualTo("/uploads/events/main.jpg");
        verify(eventService).getSlideEvents();
    }

    @Test
    void englishReadersGetTheEnglishTitleAndDescription() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "메인 슬라이드 이벤트", "메인 슬라이드 설명"),
                translation(2L, 10L, "en", "Summer event", "Summer event body")));

        EventSlideDto slide = firstSlide(Locale.ENGLISH);

        assertThat(slide.getTitle()).isEqualTo("Summer event");
        assertThat(slide.getDescription()).isEqualTo("Summer event body");
    }

    @Test
    void japaneseReadersGetTheJapaneseTitle() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "메인 슬라이드 이벤트", "메인 슬라이드 설명"),
                translation(3L, 10L, "ja", "夏のイベント", "夏のイベント本文")));

        assertThat(firstSlide(Locale.JAPANESE).getTitle()).isEqualTo("夏のイベント");
    }

    @Test
    void simplifiedAndTraditionalChineseAreCarriedThroughExactly() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(4L, 10L, "zh-CN", "简体标题", "简体正文"),
                translation(5L, 10L, "zh-TW", "繁體標題", "繁體正文")));

        assertThat(firstSlide(Locale.forLanguageTag("zh-CN")).getTitle()).isEqualTo("简体标题");
        assertThat(firstSlide(Locale.forLanguageTag("zh-TW")).getTitle()).isEqualTo("繁體標題");
    }

    @Test
    void anUnsupportedLocaleFallsBackToKorean() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "메인 슬라이드 이벤트", "메인 슬라이드 설명"),
                translation(2L, 10L, "en", "Summer event", "Summer event body")));

        assertThat(firstSlide(Locale.GERMAN).getTitle()).isEqualTo("메인 슬라이드 이벤트");
    }

    @Test
    void anEventWithoutAnyTranslationKeepsTheBaseValues() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of());

        EventSlideDto slide = firstSlide(Locale.ENGLISH);

        assertThat(slide.getTitle()).isEqualTo("메인 슬라이드 이벤트");
        assertThat(slide.getDescription()).isEqualTo("메인 슬라이드 설명");
    }

    @Test
    void titleAndDescriptionFallBackIndependently() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "메인 슬라이드 이벤트", "메인 슬라이드 설명"),
                translation(2L, 10L, "en", "Summer event", "   ")));

        EventSlideDto slide = firstSlide(Locale.ENGLISH);

        assertThat(slide.getTitle()).isEqualTo("Summer event");
        assertThat(slide.getDescription()).isEqualTo("메인 슬라이드 설명");
    }

    @Test
    void traditionalChineseDoesNotBorrowFromSimplifiedChinese() {
        when(eventService.getSlideEvents()).thenReturn(List.of(slide(10L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "메인 슬라이드 이벤트", "메인 슬라이드 설명"),
                translation(4L, 10L, "zh-CN", "简体标题", "简体正文")));

        assertThat(firstSlide(Locale.forLanguageTag("zh-TW")).getTitle())
                .isEqualTo("메인 슬라이드 이벤트");
    }

    @Test
    void everySlideReadsTranslationsInASingleQuery() {
        when(eventService.getSlideEvents())
                .thenReturn(List.of(slide(10L), slide(11L), slide(12L)));
        when(eventMapper.findTranslationsByEventIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
                translation(2L, 10L, "en", "Summer event", "Summer event body")));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(controller.slideEvents()).hasSize(3);

        verify(eventMapper, times(1)).findTranslationsByEventIds(List.of(10L, 11L, 12L));
        verify(eventMapper, never()).findTranslationsByEventId(anyLong());
    }

    @Test
    void anEmptySlideListNeverReadsTranslations() {
        when(eventService.getSlideEvents()).thenReturn(List.of());

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(controller.slideEvents()).isEmpty();

        verifyNoInteractions(eventMapper);
    }

    /* === 이미지 정책 === */

    @Test
    void theSharedMainImageStaysTheSameInEveryLanguage() {
        Event slide = slide(10L);
        slide.setEventType(EventType.INFOGRAPHIC);
        when(eventService.getSlideEvents()).thenReturn(List.of(slide));
        when(eventMapper.findTranslationsByEventIds(List.of(10L))).thenReturn(List.of(
                translationWithPoster(2L, 10L, "en", "Summer event", "/uploads/en-poster.png")));

        // 슬라이더는 예나 지금이나 대표 이미지만 쓴다. 언어별 포스터로 바꾸지 않는다.
        assertThat(firstSlide(Locale.ENGLISH).getEventImg())
                .isEqualTo("/uploads/events/main.jpg");
        assertThat(firstSlide(Locale.KOREAN).getEventImg())
                .isEqualTo("/uploads/events/main.jpg");
    }

    /* === 응답 계약 === */

    @Test
    void slideJsonCarriesOnlyWhatTheSliderRendersAndNoInternalFields() throws Exception {
        String json = new ObjectMapper().writeValueAsString(EventSlideDto.from(slide(10L)));

        assertThat(json).contains(
                "\"id\":10",
                "\"title\":\"메인 슬라이드 이벤트\"",
                "\"description\":\"메인 슬라이드 설명\"",
                "\"eventImg\":\"/uploads/events/main.jpg\"");
        // 슬라이더는 유형과 무관하게 대표 이미지만 그리므로 유형도 내려보내지 않는다.
        // 상세용 세로 인포그래픽(posterImg)도 이 응답에 없다.
        assertThat(json)
                .doesNotContain("userId")
                .doesNotContain("createdAt")
                .doesNotContain("eventType")
                .doesNotContain("posterImg")
                .doesNotContain("\"slide\"");
    }

    /* === helpers === */

    private EventSlideDto firstSlide(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        return controller.slideEvents().get(0);
    }

    private Event slide(Long id) {
        Event event = new Event();
        event.setId(id);
        event.setTitle("메인 슬라이드 이벤트");
        event.setDescription("메인 슬라이드 설명");
        event.setEventImg("/uploads/events/main.jpg");
        event.setEventType(EventType.STANDARD);
        event.setSlide(true);
        event.setUserId(3L);
        return event;
    }

    private EventTranslation translation(Long id, Long eventId, String languageCode,
                                         String title, String description) {
        return translation(id, eventId, languageCode, title, description, null);
    }

    private EventTranslation translationWithPoster(Long id, Long eventId, String languageCode,
                                                   String title, String posterImg) {
        return translation(id, eventId, languageCode, title, null, posterImg);
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
}
