package com.example.travlediary.controller.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import com.example.travlediary.service.event.EventService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventApiControllerTest {

    @Test
    void slideEndpointKeepsReturningActiveSlideEventsFromService() {
        EventService eventService = mock(EventService.class);
        Event slide = new Event();
        slide.setId(10L);
        slide.setTitle("메인 슬라이드 이벤트");
        when(eventService.getSlideEvents()).thenReturn(List.of(slide));
        EventApiController controller = new EventApiController(eventService);

        assertThat(controller.slideEvents()).containsExactly(slide);
    }

    @Test
    void eventTypeDoesNotChangeExistingSlideJsonContract() throws Exception {
        Event slide = new Event();
        slide.setId(10L);
        slide.setTitle("메인 슬라이드 이벤트");
        slide.setEventType(EventType.INFOGRAPHIC);

        String json = new ObjectMapper().writeValueAsString(slide);

        assertThat(json).contains("\"id\":10", "\"title\":\"메인 슬라이드 이벤트\"")
                .doesNotContain("eventType");
    }
}
