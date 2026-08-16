package com.example.travlediary.controller.event;

import com.example.travlediary.model.Event;
import com.example.travlediary.service.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    private EventController controller;

    @BeforeEach
    void setUp() {
        controller = new EventController(eventService);
    }

    @Test
    void eventsWithoutStatusShowsOngoingEventsByDefault() {
        Event ongoing = event(1L, "진행 중 이벤트");
        when(eventService.countEventsByStatus("ongoing")).thenReturn(1L);
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of(ongoing));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.eventList(null, 1, 9, model);

        assertThat(view).isEqualTo("event/event-list");
        assertThat(model.getAttribute("selectedStatus")).isEqualTo("ongoing");
        assertThat(model.getAttribute("eventList")).isEqualTo(List.of(ongoing));
        verify(eventService).getEventsByStatus("ongoing", 0L, 9);
    }

    @Test
    void invalidStatusSafelyFallsBackToOngoing() {
        when(eventService.countEventsByStatus("ongoing")).thenReturn(0L);
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        controller.eventList("all", 1, 9, model);

        assertThat(model.getAttribute("selectedStatus")).isEqualTo("ongoing");
        verify(eventService).getEventsByStatus("ongoing", 0L, 9);
    }

    @Test
    void supportedStatusIsPreserved() {
        when(eventService.countEventsByStatus("upcoming")).thenReturn(0L);
        when(eventService.getEventsByStatus("upcoming", 0L, 9)).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        controller.eventList("upcoming", 1, 9, model);

        assertThat(model.getAttribute("selectedStatus")).isEqualTo("upcoming");
        verify(eventService).getEventsByStatus("upcoming", 0L, 9);
    }

    @Test
    void pagingUsesOffsetAndClampsPageToTheLastAvailablePage() {
        Event ongoing = event(1L, "진행 중 이벤트");
        when(eventService.countEventsByStatus("ongoing")).thenReturn(10L);
        when(eventService.getEventsByStatus("ongoing", 9L, 9)).thenReturn(List.of(ongoing));
        ConcurrentModel model = new ConcurrentModel();

        // 마지막 페이지(2)를 넘겨서 요청해도 마지막 페이지로 맞춰진다
        controller.eventList("ongoing", 5, 9, model);

        assertThat(model.getAttribute("currentPage")).isEqualTo(2);
        assertThat(model.getAttribute("totalPages")).isEqualTo(2);
        assertThat(model.getAttribute("totalCount")).isEqualTo(10L);
        assertThat(model.getAttribute("pageSize")).isEqualTo(9);
        assertThat(model.getAttribute("pageStart")).isEqualTo(1);
        assertThat(model.getAttribute("pageEnd")).isEqualTo(2);
        verify(eventService).getEventsByStatus("ongoing", 9L, 9);
    }

    @Test
    void invalidPageSizeFallsBackToTheDefaultAndIsCapped() {
        when(eventService.countEventsByStatus("ongoing")).thenReturn(0L);
        ConcurrentModel model = new ConcurrentModel();

        controller.eventList("ongoing", 0, 0, model);
        assertThat(model.getAttribute("pageSize")).isEqualTo(9);
        assertThat(model.getAttribute("currentPage")).isEqualTo(1);

        controller.eventList("ongoing", 1, 999, model);
        assertThat(model.getAttribute("pageSize")).isEqualTo(48);
    }

    private Event event(Long id, String title) {
        Event event = new Event();
        event.setId(id);
        event.setTitle(title);
        return event;
    }
}
