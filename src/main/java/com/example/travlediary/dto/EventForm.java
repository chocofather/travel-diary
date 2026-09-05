package com.example.travlediary.dto;

import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Data
public class EventForm {
    private String title;
    private String description;
    private MultipartFile imageFile;
    private MultipartFile posterFile;
    private EventType eventType = EventType.INFOGRAPHIC;
    private boolean slide;
    private LocalDate startDate;
    private LocalDate endDate;
    private String startYear;
    private String startMonth;
    private String startDay;
    private String endYear;
    private String endMonth;
    private String endDay;

    /**
     * 언어별 번역 입력 슬롯. 0번은 한국어 자리이고 화면에 그리지 않는다.
     * 한국어는 위쪽 제목·상세 내용 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<EventTranslationForm> translations =
            EventTranslationForm.newTranslationSlots();

    public static EventForm from(Event event) {
        EventForm form = new EventForm();
        form.setTitle(event.getTitle());
        form.setDescription(event.getDescription());
        form.setEventType(event.getEventType() == null
                ? EventType.INFOGRAPHIC
                : event.getEventType());
        form.setSlide(Boolean.TRUE.equals(event.getSlide()));
        form.setStartDate(event.getStartDate());
        form.setEndDate(event.getEndDate());
        form.setDateParts(event.getStartDate(), true);
        form.setDateParts(event.getEndDate(), false);
        return form;
    }

    private void setDateParts(LocalDate date, boolean start) {
        if (date == null) {
            return;
        }
        String year = String.format("%04d", date.getYear());
        String month = String.format("%02d", date.getMonthValue());
        String day = String.format("%02d", date.getDayOfMonth());
        if (start) {
            startYear = year;
            startMonth = month;
            startDay = day;
        } else {
            endYear = year;
            endMonth = month;
            endDay = day;
        }
    }
}
