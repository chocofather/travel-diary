package com.example.travlediary.service.event;

import com.example.travlediary.dto.EventForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceAdminTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private FileUploadService fileUploadService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventMapper, fileUploadService);
    }

    /* === 일반(STANDARD) 이벤트 신규 등록 === */

    @Test
    void standardEventIsCreatedWithTextOnlyContentAndNoImages() {
        EventForm form = standardForm();
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        Event saved = capturedInsert();
        assertThat(saved.getEventType()).isEqualTo(EventType.STANDARD);
        assertThat(saved.getTitle()).isEqualTo("여름 여행 이벤트");
        assertThat(saved.getDescription()).isEqualTo("이벤트 상세 설명");
        assertThat(saved.getEventImg()).isNull();
        assertThat(saved.getPosterImg()).isNull();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        verify(fileUploadService, never()).saveFile(any(), any());
    }

    @Test
    void standardEventIsCreatedWithMainImageAndContent() {
        EventForm form = standardForm();
        form.setImageFile(image("event.jpg"));
        when(fileUploadService.saveFile(form.getImageFile(), "events"))
                .thenReturn("/uploads/events/event.jpg");
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        Event saved = capturedInsert();
        assertThat(saved.getEventImg()).isEqualTo("/uploads/events/event.jpg");
        assertThat(saved.getPosterImg()).isNull();
    }

    @Test
    void standardEventRequiresContent() {
        EventForm form = standardForm();
        form.setDescription("  ");

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("description"));

        verify(fileUploadService, never()).saveFile(any(), any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void standardEventDoesNotRequirePosterOrMainImage() {
        EventForm withoutImages = standardForm();
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(withoutImages, 7L);

        assertThat(capturedInsert().getPosterImg()).isNull();
    }

    /* === 인포그래픽(INFOGRAPHIC) 이벤트 신규 등록 === */

    @Test
    void infographicEventIsCreatedFromPosterWithoutContent() {
        EventForm form = infographicForm();
        form.setDescription(null);
        when(fileUploadService.saveFile(form.getPosterFile(), "events/posters"))
                .thenReturn("/uploads/events/posters/poster.jpg");
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        Event saved = capturedInsert();
        assertThat(saved.getEventType()).isEqualTo(EventType.INFOGRAPHIC);
        assertThat(saved.getPosterImg()).isEqualTo("/uploads/events/posters/poster.jpg");
        assertThat(saved.getDescription()).isEmpty();
        assertThat(saved.getEventImg()).isNull();
    }

    @Test
    void infographicEventRequiresPosterImage() {
        EventForm form = infographicForm();
        form.setPosterFile(null);

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("posterFile"));

        verify(fileUploadService, never()).saveFile(any(), any());
        verify(eventMapper, never()).insert(any());
    }

    /* === 메인 슬라이더 조건부 검증 === */

    @Test
    void slideRequiresRepresentativeImageOnlyWhenSlideIsEnabled() {
        EventForm withoutSlide = standardForm();
        when(eventMapper.insert(any(Event.class))).thenReturn(1);
        eventService.create(withoutSlide, 7L);
        assertThat(capturedInsert().getEventImg()).isNull();

        EventForm withSlide = standardForm();
        withSlide.setSlide(true);

        assertThatThrownBy(() -> eventService.create(withSlide, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("imageFile"));
    }

    @Test
    void slideEventWithRepresentativeImageIsAccepted() {
        EventForm form = standardForm();
        form.setSlide(true);
        form.setImageFile(image("slide.jpg"));
        when(fileUploadService.saveFile(form.getImageFile(), "events"))
                .thenReturn("/uploads/events/slide.jpg");
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        Event saved = capturedInsert();
        assertThat(saved.getSlide()).isTrue();
        assertThat(saved.getEventImg()).isEqualTo("/uploads/events/slide.jpg");
    }

    /* === 공통 필수값 및 기간 검증 === */

    @Test
    void createRejectsBlankRequiredFieldsBeforeUploading() {
        EventForm form = standardForm();
        form.setTitle("  ");
        form.setImageFile(image("event.jpg"));

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("title"));

        verify(fileUploadService, never()).saveFile(any(), any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void createRejectsMissingEventType() {
        EventForm form = standardForm();
        form.setEventType(null);

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("eventType"));
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        EventForm form = standardForm();
        form.setEndDate(LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("endDate"));

        verify(eventMapper, never()).insert(any());
    }

    @Test
    void createCombinesSplitDatePartsIntoLocalDates() {
        EventForm form = standardForm();
        form.setStartDate(null);
        form.setEndDate(null);
        form.setStartYear("2026");
        form.setStartMonth("08");
        form.setStartDay("14");
        form.setEndYear("2026");
        form.setEndMonth("09");
        form.setEndDay("03");
        when(eventMapper.insert(any(Event.class))).thenReturn(1);

        eventService.create(form, 7L);

        Event saved = capturedInsert();
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void createRejectsNonexistentSplitDate() {
        EventForm form = standardForm();
        form.setStartDate(null);
        form.setStartYear("2026");
        form.setStartMonth("02");
        form.setStartDay("31");

        assertThatThrownBy(() -> eventService.create(form, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("startDate"));

        verify(eventMapper, never()).insert(any());
    }

    @Test
    void createRejectsOutOfRangeMonthAndMissingDateParts() {
        EventForm invalidMonth = standardForm();
        invalidMonth.setStartDate(null);
        invalidMonth.setStartYear("2026");
        invalidMonth.setStartMonth("13");
        invalidMonth.setStartDay("01");

        assertThatThrownBy(() -> eventService.create(invalidMonth, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("startDate"));

        EventForm missingDate = standardForm();
        missingDate.setStartDate(null);
        assertThatThrownBy(() -> eventService.create(missingDate, 7L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("startDate"));
        verify(eventMapper, never()).insert(any());
    }

    /* === 수정 === */

    @Test
    void updateStandardEventWithoutAnyImageSucceeds() {
        Event existing = existingStandardEvent();
        existing.setEventImg(null);
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        EventForm form = standardForm();
        form.setTitle("수정된 이벤트");

        eventService.update(10L, form);

        assertThat(existing.getTitle()).isEqualTo("수정된 이벤트");
        assertThat(existing.getEventImg()).isNull();
        verify(fileUploadService, never()).saveFile(any(), any());
        verify(eventMapper).updateEvent(existing);
    }

    @Test
    void updateStandardEventWithoutNewFileKeepsExistingMainImage() {
        Event existing = existingStandardEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        EventForm form = standardForm();
        form.setSlide(true);

        eventService.update(10L, form);

        assertThat(existing.getEventImg()).isEqualTo("/uploads/events/old.jpg");
        assertThat(existing.getSlide()).isTrue();
        verify(fileUploadService, never()).saveFile(any(), any());
    }

    @Test
    void updateStandardEventStillRequiresContent() {
        Event existing = existingStandardEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        EventForm form = standardForm();
        form.setDescription(null);

        assertThatThrownBy(() -> eventService.update(10L, form))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("description"));

        assertThat(existing.getDescription()).isEqualTo("기존 설명");
        verify(eventMapper, never()).updateEvent(any());
    }

    @Test
    void updateInfographicEventKeepsExistingPosterWithoutReupload() {
        Event existing = existingInfographicEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        EventForm form = infographicForm();
        form.setPosterFile(null);
        form.setDescription(null);
        form.setTitle("수정된 인포그래픽");

        eventService.update(10L, form);

        assertThat(existing.getTitle()).isEqualTo("수정된 인포그래픽");
        assertThat(existing.getDescription()).isEmpty();
        assertThat(existing.getPosterImg()).isEqualTo("/uploads/events/posters/old.jpg");
        verify(fileUploadService, never()).saveFile(any(), any());
        verify(eventMapper).updateEvent(existing);
    }

    @Test
    void updateInfographicEventReplacesPosterWhenNewFileIsUploaded() {
        Event existing = existingInfographicEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        EventForm form = infographicForm();
        when(fileUploadService.saveFile(form.getPosterFile(), "events/posters"))
                .thenReturn("/uploads/events/posters/new.jpg");

        eventService.update(10L, form);

        assertThat(existing.getPosterImg()).isEqualTo("/uploads/events/posters/new.jpg");
        verify(eventMapper).updateEvent(existing);
    }

    @Test
    void updateInfographicEventWithoutAnyPosterIsRejected() {
        Event existing = existingInfographicEvent();
        existing.setPosterImg(null);
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        EventForm form = infographicForm();
        form.setPosterFile(null);

        assertThatThrownBy(() -> eventService.update(10L, form))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("posterFile"));

        verify(eventMapper, never()).updateEvent(any());
    }

    @Test
    void updateStoresChangedEventTypeInBothDirections() {
        Event infographic = existingInfographicEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(infographic);
        when(eventMapper.updateEvent(infographic)).thenReturn(1);

        eventService.update(10L, standardForm());

        assertThat(infographic.getEventType()).isEqualTo(EventType.STANDARD);
        assertThat(infographic.getDescription()).isEqualTo("이벤트 상세 설명");

        Event standard = existingStandardEvent();
        standard.setPosterImg("/uploads/events/posters/old.jpg");
        when(eventMapper.selectEventById(11L)).thenReturn(standard);
        when(eventMapper.updateEvent(standard)).thenReturn(1);
        EventForm toInfographic = infographicForm();
        toInfographic.setPosterFile(null);

        eventService.update(11L, toInfographic);

        assertThat(standard.getEventType()).isEqualTo(EventType.INFOGRAPHIC);
    }

    @Test
    void updateWithNewImagesReplacesBothPaths() {
        Event existing = existingStandardEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        EventForm form = standardForm();
        form.setImageFile(image("new.jpg"));
        form.setPosterFile(image("new-poster.jpg"));
        when(fileUploadService.saveFile(form.getImageFile(), "events"))
                .thenReturn("/uploads/events/new.jpg");
        when(fileUploadService.saveFile(form.getPosterFile(), "events/posters"))
                .thenReturn("/uploads/events/posters/new.jpg");

        eventService.update(10L, form);

        assertThat(existing.getEventImg()).isEqualTo("/uploads/events/new.jpg");
        assertThat(existing.getPosterImg()).isEqualTo("/uploads/events/posters/new.jpg");
        verify(eventMapper).updateEvent(existing);
    }

    @Test
    void updateMissingEventReturnsNotFound() {
        when(eventMapper.selectEventById(99L)).thenReturn(null);

        assertThatThrownBy(() -> eventService.update(99L, standardForm()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(eventMapper, never()).updateEvent(any());
    }

    @Test
    void updateRejectsInvalidPeriodWithoutChangingStoredEvent() {
        Event existing = existingStandardEvent();
        when(eventMapper.selectEventById(10L)).thenReturn(existing);
        EventForm form = standardForm();
        form.setEndDate(LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> eventService.update(10L, form))
                .isInstanceOfSatisfying(EventValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("endDate"));

        assertThat(existing.getTitle()).isEqualTo("기존 이벤트");
        verify(eventMapper, never()).updateEvent(any());
    }

    @Test
    void deleteWithoutImagesKeepsExistingMapperFlow() {
        Event existing = existingStandardEvent();
        existing.setEventImg(null);
        existing.setPosterImg(null);
        when(eventMapper.selectEventById(10L)).thenReturn(existing);

        eventService.deleteEventById(10L);

        verify(eventMapper).deleteEventById(10L);
    }

    private Event capturedInsert() {
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventMapper).insert(captor.capture());
        return captor.getValue();
    }

    private EventForm standardForm() {
        EventForm form = baseForm();
        form.setEventType(EventType.STANDARD);
        form.setDescription("이벤트 상세 설명");
        return form;
    }

    private EventForm infographicForm() {
        EventForm form = baseForm();
        form.setEventType(EventType.INFOGRAPHIC);
        form.setPosterFile(image("poster.jpg"));
        return form;
    }

    private EventForm baseForm() {
        EventForm form = new EventForm();
        form.setTitle("여름 여행 이벤트");
        form.setSlide(false);
        form.setStartDate(LocalDate.of(2026, 8, 1));
        form.setEndDate(LocalDate.of(2026, 8, 31));
        return form;
    }

    private Event existingStandardEvent() {
        Event event = existingEvent();
        event.setEventType(EventType.STANDARD);
        event.setEventImg("/uploads/events/old.jpg");
        return event;
    }

    private Event existingInfographicEvent() {
        Event event = existingEvent();
        event.setEventType(EventType.INFOGRAPHIC);
        event.setPosterImg("/uploads/events/posters/old.jpg");
        return event;
    }

    private Event existingEvent() {
        Event event = new Event();
        event.setId(10L);
        event.setTitle("기존 이벤트");
        event.setDescription("기존 설명");
        event.setSlide(false);
        event.setUserId(3L);
        event.setStartDate(LocalDate.of(2026, 7, 1));
        event.setEndDate(LocalDate.of(2026, 7, 31));
        return event;
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("image", name, "image/jpeg", new byte[]{1, 2, 3});
    }
}