package com.example.travlediary.service.event;

import com.example.travlediary.controller.event.EventController;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.ConcurrentModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 공개 이벤트 목록·상세에 붙인 언어 대체가 화면 값만 바꾸는지 본다.
 *
 * <p>제목·본문은 필드마다 따로 대체되고, 인포그래픽 포스터는 요청 언어 → 한국어 → base 까지만 본다.
 * 상태·정렬·페이징·유형은 원본 값을 그대로 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class EventPublicLocalizationTest {

    private static final String BASE_POSTER = "/uploads/events/posters/base.png";

    @Mock private EventService eventService;
    @Mock private EventMapper eventMapper;

    private EventController controller;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        controller = new EventController(eventService, new EventLocalizationService(eventMapper));
        originalLocale = LocaleContextHolder.getLocale();
    }

    @AfterEach
    void restoreLocale() {
        LocaleContextHolder.setLocale(originalLocale);
    }

    /* === 목록 === */

    @Test
    void englishListShowsTheEnglishTitleAndDescription() {
        List<Event> events = List.of(infographic(1L));
        stubOngoingPage(events);
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", "한국어 본문", null),
                translation(2L, 1L, "en", "English title", "English body", null)));

        Event card = firstCard(Locale.ENGLISH);

        assertThat(card.getTitle()).isEqualTo("English title");
        assertThat(card.getDescription()).isEqualTo("English body");
    }

    @Test
    void japaneseListShowsTheJapaneseTitle() {
        stubOngoingPage(List.of(infographic(1L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", "한국어 본문", null),
                translation(3L, 1L, "ja", "日本語タイトル", "日本語本文", null)));

        assertThat(firstCard(Locale.JAPANESE).getTitle()).isEqualTo("日本語タイトル");
    }

    @Test
    void simplifiedAndTraditionalChineseAreCarriedThroughExactly() {
        stubOngoingPage(List.of(infographic(1L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(4L, 1L, "zh-CN", "简体标题", "简体正文", null),
                translation(5L, 1L, "zh-TW", "繁體標題", "繁體正文", null)));

        assertThat(firstCard(Locale.forLanguageTag("zh-CN")).getTitle()).isEqualTo("简体标题");

        stubOngoingPage(List.of(infographic(1L)));
        assertThat(firstCard(Locale.forLanguageTag("zh-TW")).getTitle()).isEqualTo("繁體標題");
    }

    @Test
    void anUnsupportedLocaleFallsBackToKorean() {
        stubOngoingPage(List.of(infographic(1L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", "한국어 본문", null),
                translation(2L, 1L, "en", "English title", "English body", null)));

        assertThat(firstCard(Locale.GERMAN).getTitle()).isEqualTo("한국어 제목");
    }

    @Test
    void aWholePageOfCardsReadsTranslationsInASingleQuery() {
        stubOngoingPage(List.of(infographic(1L), infographic(2L), infographic(3L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                translation(1L, 1L, "en", "First", "First body", null)));

        cards(Locale.ENGLISH);

        verify(eventMapper, times(1)).findTranslationsByEventIds(List.of(1L, 2L, 3L));
        verify(eventMapper, never()).findTranslationsByEventId(anyLong());
    }

    @Test
    void anEmptyPageNeverReadsTranslations() {
        stubOngoingPage(List.of());

        assertThat(cards(Locale.ENGLISH)).isEmpty();
        verifyNoInteractions(eventMapper);
    }

    @Test
    void localizationNeverMutatesTheEventsItWasGiven() {
        Event base = infographic(1L);
        stubOngoingPage(List.of(base));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(2L, 1L, "en", "English title", "English body", "/en.png")));

        assertThat(firstCard(Locale.ENGLISH).getTitle()).isEqualTo("English title");
        assertThat(base.getTitle()).isEqualTo("기본 제목");
        assertThat(base.getDescription()).isEqualTo("기본 본문");
        assertThat(base.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void statusSortingAndPagingStayOnTheOriginalValues() {
        when(eventService.countEventsByStatus("upcoming")).thenReturn(20L);
        when(eventService.getEventsByStatus("upcoming", 9L, 9)).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        controller.eventList("upcoming", 2, 9, model);

        assertThat(model.getAttribute("selectedStatus")).isEqualTo("upcoming");
        assertThat(model.getAttribute("currentPage")).isEqualTo(2);
        assertThat(model.getAttribute("pageSize")).isEqualTo(9);
        assertThat(model.getAttribute("totalPages")).isEqualTo(3);
        verify(eventService).getEventsByStatus("upcoming", 9L, 9);
    }

    /* === 상세 === */

    @Test
    void detailShowsTheRequestedLanguageTitleAndDescription() {
        Event base = standard(1L);
        when(eventService.getEventDetail(1L)).thenReturn(base);
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", "한국어 본문", null),
                translation(2L, 1L, "en", "English title", "English body", null)));

        Event detail = detail(Locale.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("English title");
        assertThat(detail.getDescription()).isEqualTo("English body");
        // 유형·기간·노출 여부는 원본 그대로다.
        assertThat(detail.getEventType()).isEqualTo(EventType.STANDARD);
        assertThat(detail.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(detail.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(detail.getSlide()).isFalse();
    }

    @Test
    void titleAndDescriptionFallBackIndependentlyOnTheDetailPage() {
        when(eventService.getEventDetail(1L)).thenReturn(standard(1L));
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", "한국어 본문", null),
                translation(2L, 1L, "en", "English title", "  ", null)));

        Event detail = detail(Locale.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("English title");
        assertThat(detail.getDescription()).isEqualTo("한국어 본문");
    }

    /* === 인포그래픽 포스터 === */

    @Test
    void infographicUsesTheRequestedLanguagePoster() {
        when(eventService.getEventDetail(1L)).thenReturn(infographic(1L));
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", null, "/ko.png"),
                translation(2L, 1L, "en", "English title", null, "/en.png")));

        assertThat(detail(Locale.ENGLISH).getPosterImg()).isEqualTo("/en.png");
    }

    @Test
    void infographicFallsBackToTheKoreanPosterWhenTheRequestedLanguageHasNone() {
        when(eventService.getEventDetail(1L)).thenReturn(infographic(1L));
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", null, "/ko.png"),
                translation(2L, 1L, "en", "English title", null, "/en.png"),
                translation(3L, 1L, "ja", "日本語タイトル", null, null)));

        // 일본어 화면에 영어 포스터를 띄우지 않는다. 읽을 수 있는 한국어 포스터가 먼저다.
        assertThat(detail(Locale.JAPANESE).getPosterImg()).isEqualTo("/ko.png");
    }

    @Test
    void infographicFallsBackToTheBasePosterWhenKoreanHasNoneEither() {
        when(eventService.getEventDetail(1L)).thenReturn(infographic(1L));
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(1L, 1L, "ko", "한국어 제목", null, null),
                translation(2L, 1L, "en", "English title", null, "/en.png")));

        assertThat(detail(Locale.JAPANESE).getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void infographicListCardsUseTheLocalizedPosterToo() {
        stubOngoingPage(List.of(infographic(1L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(2L, 1L, "en", "English title", null, "/en.png")));

        Event card = firstCard(Locale.ENGLISH);

        assertThat(card.getPosterImg()).isEqualTo("/en.png");
        assertThat(card.getEventType()).isEqualTo(EventType.INFOGRAPHIC);
    }

    /* === 일반 이벤트 이미지 === */

    @Test
    void standardEventsKeepTheirSharedMainImageInEveryLanguage() {
        when(eventService.getEventDetail(1L)).thenReturn(standard(1L));
        when(eventMapper.findTranslationsByEventId(1L)).thenReturn(List.of(
                translation(2L, 1L, "en", "English title", "English body", "/en.png")));

        Event detail = detail(Locale.ENGLISH);

        // 대표 이미지는 언어 공용이고, 일반 이벤트는 언어별 포스터를 쓰지 않는다.
        assertThat(detail.getEventImg()).isEqualTo("/uploads/events/main.png");
        assertThat(detail.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void standardListCardsIgnoreTheTranslationPosterAsWell() {
        stubOngoingPage(List.of(standard(1L)));
        when(eventMapper.findTranslationsByEventIds(List.of(1L))).thenReturn(List.of(
                translation(2L, 1L, "en", "English title", "English body", "/en.png")));

        Event card = firstCard(Locale.ENGLISH);

        assertThat(card.getEventImg()).isEqualTo("/uploads/events/main.png");
        assertThat(card.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    /* === helpers === */

    private void stubOngoingPage(List<Event> events) {
        when(eventService.countEventsByStatus("ongoing")).thenReturn((long) events.size());
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(events);
    }

    @SuppressWarnings("unchecked")
    private List<Event> cards(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        ConcurrentModel model = new ConcurrentModel();
        controller.eventList("ongoing", 1, 9, model);
        return (List<Event>) model.getAttribute("eventList");
    }

    private Event firstCard(Locale locale) {
        return cards(locale).get(0);
    }

    private Event detail(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        ConcurrentModel model = new ConcurrentModel();
        controller.eventDetail(1L, model);
        return (Event) model.getAttribute("event");
    }

    private Event infographic(Long id) {
        Event event = event(id);
        event.setEventType(EventType.INFOGRAPHIC);
        return event;
    }

    private Event standard(Long id) {
        Event event = event(id);
        event.setEventType(EventType.STANDARD);
        return event;
    }

    private Event event(Long id) {
        Event event = new Event();
        event.setId(id);
        event.setTitle("기본 제목");
        event.setDescription("기본 본문");
        event.setEventImg("/uploads/events/main.png");
        event.setPosterImg(BASE_POSTER);
        event.setSlide(false);
        event.setUserId(3L);
        event.setStartDate(LocalDate.of(2026, 8, 1));
        event.setEndDate(LocalDate.of(2026, 8, 31));
        return event;
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
