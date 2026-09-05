package com.example.travlediary.service.event;

import com.example.travlediary.dto.EventForm;
import com.example.travlediary.dto.EventTranslationForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 이벤트 번역 탭의 복원과 저장 규칙을 본다.
 *
 * <p>한국어 줄은 폼이 아니라 events 원문을 따르고, 외국어 줄은 언어마다 따로 처리한다.
 * 이번 단계에서 폼이 다루지 않는 언어별 포스터는 저장 과정에서 사라지면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class EventTranslationSaveTest {

    private static final Long EVENT_ID = 10L;
    private static final String BASE_TITLE = "여름 여행 이벤트";
    private static final String BASE_DESCRIPTION = "이벤트 상세 설명";
    private static final String BASE_POSTER = "/uploads/events/posters/base.png";

    @Mock private EventMapper eventMapper;
    @Mock private FileUploadService fileUploadService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventMapper, fileUploadService);
    }

    /* === 슬롯과 복원 === */

    @Test
    void newEventFormStartsWithCanonicalLanguageSlots() {
        assertThat(new EventForm().getTranslations())
                .extracting(EventTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(eventService.getTranslationForms(null))
                .extracting(EventTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
    }

    @Test
    void storedTranslationsArePreloadedByLanguageCodeAndMissingOnesStayEmpty() {
        // 저장된 줄 순서에 기대지 않는다. 언어 코드로 찾아 채운다.
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("zh-TW", "繁體標題", "繁體本文", null),
                translation("en", "English title", "English body", "/en.png")));

        List<EventTranslationForm> slots = eventService.getTranslationForms(EVENT_ID);

        assertThat(slotOf(slots, "en").getTitle()).isEqualTo("English title");
        assertThat(slotOf(slots, "en").getDescription()).isEqualTo("English body");
        assertThat(slotOf(slots, "zh-TW").getTitle()).isEqualTo("繁體標題");
        assertThat(slotOf(slots, "ja").getTitle()).isEmpty();
        assertThat(slotOf(slots, "ja").getDescription()).isEmpty();
        assertThat(slotOf(slots, "zh-CN").getTitle()).isEmpty();
    }

    @Test
    void preloadSkipsLanguagesThatHaveNoSlot() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                translation("zh", "옛 코드", "옛 본문", null),
                translation(null, "언어 없음", "본문", null),
                translation("en", "English title", null, null)));

        List<EventTranslationForm> slots = eventService.getTranslationForms(EVENT_ID);

        assertThat(slots).hasSize(5);
        assertThat(slotOf(slots, "en").getTitle()).isEqualTo("English title");
        // description 이 null 이면 빈 문자열로 복원한다.
        assertThat(slotOf(slots, "en").getDescription()).isEmpty();
    }

    /* === 한국어 동기화 === */

    @Test
    void koreanRowIsInsertedFromBaseValuesWhenItDoesNotExist() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of());

        save(List.of());

        EventTranslation korean = capturedInsert();
        assertThat(korean.getEventId()).isEqualTo(EVENT_ID);
        assertThat(korean.getLanguageCode()).isEqualTo("ko");
        assertThat(korean.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(korean.getDescription()).isEqualTo(BASE_DESCRIPTION);
        assertThat(korean.getPosterImg()).isEqualTo(BASE_POSTER);
    }

    @Test
    void koreanRowIsUpdatedFromBaseValuesWhenItAlreadyExists() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", "예전 한국어 제목", "예전 한국어 본문", "/uploads/old-ko.png")));

        save(List.of());

        EventTranslation korean = capturedUpdate();
        assertThat(korean.getLanguageCode()).isEqualTo("ko");
        // 폼 값이 아니라 base 값으로 맞춘다.
        assertThat(korean.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(korean.getDescription()).isEqualTo(BASE_DESCRIPTION);
        assertThat(korean.getPosterImg()).isEqualTo(BASE_POSTER);
        verify(eventMapper, never()).insertTranslation(any());
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void koreanFormSlotNeverOverwritesTheBaseValues() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of());

        save(List.of(form("ko", "폼에서 들어온 한국어", "폼에서 들어온 본문")));

        // ko 슬롯이 실려 와도 저장 값은 base 그대로다. 줄도 하나뿐이다.
        EventTranslation korean = capturedInsert();
        assertThat(korean.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(korean.getDescription()).isEqualTo(BASE_DESCRIPTION);
        verify(eventMapper, times(1)).insertTranslation(any());
    }

    /* === 외국어 저장 === */

    @Test
    void foreignLanguageRowIsInsertedWhenItDoesNotExist() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(List.of(form("en", "  English title  ", "  English body  ")));

        EventTranslation inserted = capturedInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("en");
        // 저장 값은 base 와 같게 strip 한다.
        assertThat(inserted.getTitle()).isEqualTo("English title");
        assertThat(inserted.getDescription()).isEqualTo("English body");
        assertThat(inserted.getPosterImg()).isNull();
    }

    @Test
    void foreignLanguageRowIsUpdatedWhenItAlreadyExists() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("ja", "古いタイトル", "古い本文", null)));

        save(List.of(form("ja", "新しいタイトル", "新しい本文")));

        EventTranslation japanese = capturedUpdates().stream()
                .filter(translation -> "ja".equals(translation.getLanguageCode()))
                .findFirst()
                .orElseThrow();
        assertThat(japanese.getTitle()).isEqualTo("新しいタイトル");
        assertThat(japanese.getDescription()).isEqualTo("新しい本文");
        verify(eventMapper, never()).insertTranslation(any());
    }

    @Test
    void titleOnlyStillKeepsTheLanguageRow() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(List.of(form("en", "English title", "   ")));

        EventTranslation inserted = capturedInsert();
        assertThat(inserted.getTitle()).isEqualTo("English title");
        assertThat(inserted.getDescription()).isNull();
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void descriptionOnlyStillKeepsTheLanguageRow() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(List.of(form("en", "", "English body")));

        EventTranslation inserted = capturedInsert();
        assertThat(inserted.getTitle()).isNull();
        assertThat(inserted.getDescription()).isEqualTo("English body");
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void blankValuesWithoutAStoredPosterRemoveTheLanguageRow() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("en", "English title", "English body", null)));

        save(List.of(form("en", "   ", "")));

        verify(eventMapper).deleteTranslation(EVENT_ID, "en");
        verify(eventMapper, never()).insertTranslation(any());
    }

    @Test
    void blankValuesNeverCreateARowThatDidNotExist() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(List.of(form("en", "", "  ")));

        verify(eventMapper, never()).insertTranslation(any());
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    /* === 언어별 포스터 보존 === */

    @Test
    void blankValuesKeepTheRowWhenAStoredPosterWouldBeLost() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("en", "English title", "English body", "/uploads/en-poster.png")));

        save(List.of(form("en", "  ", "")));

        // 줄을 지우면 언어별 포스터가 함께 사라진다. 글자만 비운다.
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
        EventTranslation english = updateOf("en");
        assertThat(english.getTitle()).isNull();
        assertThat(english.getDescription()).isNull();
        assertThat(english.getPosterImg()).isEqualTo("/uploads/en-poster.png");
    }

    @Test
    void ordinaryTranslationSaveNeverDropsTheStoredPoster() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("ja", "古いタイトル", null, "/uploads/ja-poster.png")));

        save(List.of(form("ja", "新しいタイトル", "新しい本文")));

        EventTranslation japanese = updateOf("ja");
        assertThat(japanese.getTitle()).isEqualTo("新しいタイトル");
        // 폼이 다루지 않는 값이라도 저장돼 있던 포스터는 그대로 다시 쓴다.
        assertThat(japanese.getPosterImg()).isEqualTo("/uploads/ja-poster.png");
    }

    @Test
    void savingOneLanguageLeavesTheOtherLanguageRowsAlone() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("en", "English title", "English body", "/uploads/en-poster.png"),
                translation("ja", "日本語タイトル", "日本語本文", null)));

        // 화면에서 일본어만 손댔다. 나머지 언어 슬롯은 저장된 값 그대로 실려 온다.
        save(List.of(form("en", "English title", "English body"),
                form("ja", "新しいタイトル", "新しい本文"),
                form("zh-CN", "", ""),
                form("zh-TW", "", "")));

        assertThat(updateOf("en").getPosterImg()).isEqualTo("/uploads/en-poster.png");
        assertThat(updateOf("ja").getTitle()).isEqualTo("新しいタイトル");
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
        verify(eventMapper, never()).insertTranslation(any());
    }

    /* === 언어 코드 방어 === */

    @Test
    void unsupportedLanguageCodesAreIgnored() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(Arrays.asList(
                form("zh", "옛 코드", "옛 본문"),
                form("EN", "대문자 코드", "본문"),
                form("en-US", "지역 코드", "본문"),
                form("klingon", "임의 코드", "본문"),
                form(null, "코드 없음", "본문"),
                null));

        verify(eventMapper, never()).insertTranslation(any());
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
        // ko 줄만 base 로 다시 맞춘다.
        assertThat(capturedUpdates()).hasSize(1);
        assertThat(capturedUpdates().get(0).getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void duplicateLanguageCodeUsesTheFirstSlotOnly() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER)));

        save(List.of(form("en", "First English", "First body"),
                form("en", "Second English", "Second body")));

        // UNIQUE(event_id, language_code) 를 건드리지 않도록 한 번만 쓴다.
        verify(eventMapper, times(1)).insertTranslation(any());
        assertThat(capturedInsert().getTitle()).isEqualTo("First English");
    }

    @Test
    void storedTranslationsAreReadOnlyOncePerSave() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER),
                translation("en", "English title", "English body", null)));

        save(List.of(form("en", "New English", "New body"),
                form("ja", "日本語", "日本語本文"),
                form("zh-CN", "简体", "简体正文"),
                form("zh-TW", "繁體", "繁體正文")));

        verify(eventMapper, times(1)).findTranslationsByEventId(EVENT_ID);
    }

    /* === create / update 연결 === */

    @Test
    void createSavesTranslationsForTheNewlyGeneratedEventId() {
        when(eventMapper.insert(any(Event.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Event.class).setId(21L);
            return 1;
        });

        EventForm form = standardForm();
        form.getTranslations().get(1).setTitle("English title");
        form.getTranslations().get(1).setDescription("English body");

        eventService.create(form, 7L);

        List<EventTranslation> inserted = capturedInserts();
        assertThat(inserted).extracting(EventTranslation::getEventId)
                .containsOnly(21L);
        assertThat(inserted).extracting(EventTranslation::getLanguageCode)
                .containsExactlyInAnyOrder("ko", "en");
        assertThat(inserted.stream()
                .filter(translation -> "ko".equals(translation.getLanguageCode()))
                .findFirst().orElseThrow().getTitle())
                .isEqualTo("여름 여행 이벤트");
    }

    @Test
    void updateSyncsKoreanWithTheValuesItJustSaved() {
        Event existing = existingEvent();
        when(eventMapper.selectEventById(EVENT_ID)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("ko", "예전 한국어", "예전 본문", null)));

        EventForm form = standardForm();
        form.setTitle("수정된 이벤트");
        form.setDescription("수정된 설명");

        eventService.update(EVENT_ID, form);

        EventTranslation korean = updateOf("ko");
        assertThat(korean.getTitle()).isEqualTo("수정된 이벤트");
        assertThat(korean.getDescription()).isEqualTo("수정된 설명");
    }

    /* === helpers === */

    private void save(List<EventTranslationForm> forms) {
        eventService.saveTranslations(
                EVENT_ID, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER, forms);
    }

    private EventTranslation capturedInsert() {
        return capturedInserts().get(0);
    }

    private List<EventTranslation> capturedInserts() {
        ArgumentCaptor<EventTranslation> captor =
                ArgumentCaptor.forClass(EventTranslation.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce()).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private EventTranslation capturedUpdate() {
        return capturedUpdates().get(0);
    }

    private List<EventTranslation> capturedUpdates() {
        ArgumentCaptor<EventTranslation> captor =
                ArgumentCaptor.forClass(EventTranslation.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce()).updateTranslation(captor.capture());
        return captor.getAllValues();
    }

    private EventTranslation updateOf(String languageCode) {
        return capturedUpdates().stream()
                .filter(translation -> languageCode.equals(translation.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private EventTranslationForm slotOf(List<EventTranslationForm> slots, String languageCode) {
        return slots.stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private EventTranslationForm form(String languageCode, String title, String description) {
        return new EventTranslationForm(languageCode, title, description);
    }

    private EventTranslation translation(String languageCode, String title,
                                         String description, String posterImg) {
        EventTranslation translation = new EventTranslation();
        translation.setEventId(EVENT_ID);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setDescription(description);
        translation.setPosterImg(posterImg);
        return translation;
    }

    private EventForm standardForm() {
        EventForm form = new EventForm();
        form.setTitle("여름 여행 이벤트");
        form.setEventType(EventType.STANDARD);
        form.setDescription("이벤트 상세 설명");
        form.setSlide(false);
        form.setStartDate(LocalDate.of(2026, 8, 1));
        form.setEndDate(LocalDate.of(2026, 8, 31));
        return form;
    }

    private Event existingEvent() {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setTitle("기존 이벤트");
        event.setDescription("기존 설명");
        event.setEventType(EventType.STANDARD);
        event.setEventImg("/uploads/events/old.jpg");
        event.setSlide(false);
        event.setUserId(3L);
        event.setStartDate(LocalDate.of(2026, 7, 1));
        event.setEndDate(LocalDate.of(2026, 7, 31));
        return event;
    }
}
