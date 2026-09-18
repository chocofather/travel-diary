package com.example.travlediary.service.event;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.EventForm;
import com.example.travlediary.dto.EventTranslationForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이벤트 상세 내용은 공개 화면에서 HTML 로 나가므로(th:utext) 게시글 본문과 같은 허용 정책을 거친다.
 *
 * <p>허용 목록 자체는 {@code PostContentSanitizerTest} 가 본다. 여기서는 이벤트의
 * 저장 경로(등록·수정·번역)와 상세 표시 경로에 그 정책이 실제로 연결돼 있는지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class EventDescriptionSanitizeTest {

    private static final String MALICIOUS = """
            <p>정상 <strong>서식</strong>은 남는다</p>
            <ul><li>목록</li></ul>
            <a href="https://travel.example.com">안전한 링크</a>
            <script>alert(1)</script>
            <img src="x" onerror="alert(1)">
            <p onclick="steal()">핸들러</p>
            <a href="javascript:alert(1)">위험한 링크</a>
            <iframe src="https://evil.example.com"></iframe>""";

    @Mock private EventMapper eventMapper;
    @Mock private FileUploadService fileUploadService;

    private EventService eventService;
    private EventLocalizationService localizationService;

    @BeforeEach
    void setUp() {
        PostContentSanitizer sanitizer = new PostContentSanitizer();
        eventService = new EventService(eventMapper, fileUploadService, sanitizer);
        localizationService = new EventLocalizationService(eventMapper, sanitizer);
    }

    /** 신규 등록에서 실행 가능한 조각은 저장되기 전에 사라지고 정상 서식만 남는다. */
    @Test
    void createStoresSanitizedDescription() {
        EventForm form = standardForm();
        form.setDescription(MALICIOUS);
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventMapper).insert(captor.capture());
        assertKeepsFormattingWithoutScripts(captor.getValue().getDescription());
    }

    /** 수정도 같은 경로를 쓴다. 등록만 막고 수정으로 넣을 수 있는 구멍은 없다. */
    @Test
    void updateStoresSanitizedDescription() {
        Event stored = existingEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(stored);
        when(eventMapper.updateEvent(any(Event.class))).thenReturn(1);

        EventForm form = standardForm();
        form.setDescription(MALICIOUS);
        eventService.update(10L, form);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventMapper).updateEvent(captor.capture());
        assertKeepsFormattingWithoutScripts(captor.getValue().getDescription());
    }

    /** 검증 실패로 되돌아가는 화면도 걸러진 값을 본다. 폼에 원문이 남지 않는다. */
    @Test
    void theFormCarriesBackTheSanitizedDescription() {
        EventForm form = standardForm();
        form.setDescription(MALICIOUS);
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        assertKeepsFormattingWithoutScripts(form.getDescription());
    }

    /** 외국어 번역 본문도 한국어 원문과 같은 정책을 거친다. */
    @Test
    void foreignTranslationDescriptionIsSanitized() {
        EventForm form = standardForm();
        form.setDescription("정상 내용");
        List<EventTranslationForm> translations = EventTranslationForm.newTranslationSlots();
        EventTranslationForm english = translations.stream()
                .filter(slot -> "en".equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
        english.setTitle("Summer event");
        english.setDescription(MALICIOUS);
        form.setTranslations(translations);

        when(eventMapper.insert(any(Event.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Event.class).setId(10L);
            return 1;
        });
        when(eventMapper.findTranslationsByEventId(anyLong())).thenReturn(List.of());

        eventService.create(form, 7L);

        ArgumentCaptor<EventTranslation> captor = ArgumentCaptor.forClass(EventTranslation.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce())
                .insertTranslation(captor.capture());
        EventTranslation saved = captor.getAllValues().stream()
                .filter(translation -> "en".equals(translation.getLanguageCode()))
                .findFirst()
                .orElseThrow();
        assertKeepsFormattingWithoutScripts(saved.getDescription());
    }

    /**
     * sanitize 를 붙이기 전에 저장된 줄이 남아 있을 수 있어, 상세 표시 직전에도 같은 정책을 적용한다.
     * (목록은 th:text 라 여기서만 처리한다)
     */
    @Test
    void theDetailViewSanitizesDescriptionsStoredBeforeThisChange() {
        Event legacy = existingEvent();
        legacy.setDescription(MALICIOUS);
        when(eventMapper.findTranslationsByEventId(10L)).thenReturn(List.of());

        Event display = localizationService.localize(legacy, SupportedLanguage.KOREAN);

        assertKeepsFormattingWithoutScripts(display.getDescription());
        // 원본 객체는 건드리지 않는다 (표시용 복사본만 바뀐다)
        assertThat(legacy.getDescription()).isEqualTo(MALICIOUS);
    }

    private void assertKeepsFormattingWithoutScripts(String description) {
        assertThat(description)
                .contains("<strong>서식</strong>")
                .contains("<ul>")
                .contains("<li>목록</li>")
                .contains("href=\"https://travel.example.com\"");
        assertThat(description)
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("onclick")
                .doesNotContain("javascript:")
                .doesNotContain("<iframe")
                .doesNotContain("alert(1)");
    }

    private EventForm standardForm() {
        EventForm form = new EventForm();
        form.setTitle("여름 여행 이벤트");
        form.setEventType(EventType.STANDARD);
        form.setSlide(false);
        form.setStartDate(LocalDate.of(2026, 8, 1));
        form.setEndDate(LocalDate.of(2026, 8, 31));
        return form;
    }

    private Event existingEvent() {
        Event event = new Event();
        event.setId(10L);
        event.setTitle("기존 이벤트");
        event.setDescription("기존 설명");
        event.setEventType(EventType.STANDARD);
        event.setSlide(false);
        event.setUserId(3L);
        event.setStartDate(LocalDate.of(2026, 7, 1));
        event.setEndDate(LocalDate.of(2026, 7, 31));
        return event;
    }
}
