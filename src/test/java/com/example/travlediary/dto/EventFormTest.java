package com.example.travlediary.dto;

import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EventFormTest {

    @Test
    void editFormSplitsExistingLocalDatesIntoYearMonthDayFields() {
        Event event = new Event();
        event.setStartDate(LocalDate.of(2026, 8, 4));
        event.setEndDate(LocalDate.of(2030, 9, 13));

        EventForm form = EventForm.from(event);

        assertThat(form.getStartYear()).isEqualTo("2026");
        assertThat(form.getStartMonth()).isEqualTo("08");
        assertThat(form.getStartDay()).isEqualTo("04");
        assertThat(form.getEndYear()).isEqualTo("2030");
        assertThat(form.getEndMonth()).isEqualTo("09");
        assertThat(form.getEndDay()).isEqualTo("13");
    }

    @Test
    void editFormKeepsStoredTypeAndUsesInfographicForLegacyRows() {
        Event standard = new Event();
        standard.setEventType(EventType.STANDARD);
        Event legacy = new Event();

        assertThat(EventForm.from(standard).getEventType()).isEqualTo(EventType.STANDARD);
        assertThat(EventForm.from(legacy).getEventType()).isEqualTo(EventType.INFOGRAPHIC);
        assertThat(new EventForm().getEventType()).isEqualTo(EventType.INFOGRAPHIC);
    }
}
