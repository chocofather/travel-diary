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
        when(eventService.getEventsByStatus("ongoing")).thenReturn(List.of(ongoing));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.eventList(null, model);

        assertThat(view).isEqualTo("event/event-list");
        assertThat(model.getAttribute("selectedStatus")).isEqualTo("ongoing");
        assertThat(model.getAttribute("eventList")).isEqualTo(List.of(ongoing));
        verify(eventService).getEventsByStatus("ongoing");
    }

    @Test
    void invalidStatusSafelyFallsBackToOngoing() {
        when(eventService.getEventsByStatus("ongoing")).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        controller.eventList("all", model);

        assertThat(model.getAttribute("selectedStatus")).isEqualTo("ongoing");
        verify(eventService).getEventsByStatus("ongoing");
    }

    @Test
    void supportedStatusIsPreserved() {
        when(eventService.getEventsByStatus("upcoming")).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        controller.eventList("upcoming", model);

        assertThat(model.getAttribute("selectedStatus")).isEqualTo("upcoming");
        verify(eventService).getEventsByStatus("upcoming");
    }

    private Event event(Long id, String title) {
        Event event = new Event();
        event.setId(id);
        event.setTitle(title);
        return event;
    }
}
