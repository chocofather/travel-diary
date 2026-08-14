package com.example.travlediary.service.event;

import com.example.travlediary.model.Event;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceStatusTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private FileUploadService fileUploadService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventMapper, fileUploadService);
    }

    @Test
    void missingStatusQueriesOnlyOngoingEvents() {
        Event ongoing = new Event();
        ongoing.setId(1L);
        when(eventMapper.selectOngoingEvents()).thenReturn(List.of(ongoing));

        assertThat(eventService.getEventsByStatus(null)).containsExactly(ongoing);
        verify(eventMapper, never()).selectAllEvents();
    }

    @Test
    void invalidStatusQueriesOnlyOngoingEvents() {
        when(eventMapper.selectOngoingEvents()).thenReturn(List.of());

        assertThat(eventService.getEventsByStatus("all")).isEmpty();
        verify(eventMapper, never()).selectAllEvents();
    }

    @Test
    void eachSupportedStatusUsesItsExistingDateQuery() {
        Event ongoing = new Event();
        Event upcoming = new Event();
        Event ended = new Event();
        when(eventMapper.selectOngoingEvents()).thenReturn(List.of(ongoing));
        when(eventMapper.selectUpcomingEvents()).thenReturn(List.of(upcoming));
        when(eventMapper.selectEndedEvents()).thenReturn(List.of(ended));

        assertThat(eventService.getEventsByStatus("ongoing")).containsExactly(ongoing);
        assertThat(eventService.getEventsByStatus("upcoming")).containsExactly(upcoming);
        assertThat(eventService.getEventsByStatus("ended")).containsExactly(ended);
    }

    @Test
    void slideApiServiceContractStillUsesSlideQuery() {
        Event slide = new Event();
        when(eventMapper.selectSlideEvents()).thenReturn(List.of(slide));

        assertThat(eventService.getSlideEvents()).containsExactly(slide);
    }
}
