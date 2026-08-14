package com.example.travlediary.service.event;

import com.example.travlediary.dto.EventForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;
    private final FileUploadService fileUploadService;

    @Value("${custom.upload-path}")
    private String uploadDir;

    public List<Event> getSlideEvents() {
        return eventMapper.selectSlideEvents();
    }

    @Transactional
    public void create(EventForm form, Long userId) {
        ValidatedEvent validated = validate(form, null);
        if (userId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }

        Event event = new Event();
        applyEditableFields(event, validated, form);
        if (hasUpload(form.getImageFile())) {
            event.setEventImg(fileUploadService.saveFile(form.getImageFile(), "events"));
        }
        if (hasUpload(form.getPosterFile())) {
            event.setPosterImg(fileUploadService.saveFile(form.getPosterFile(), "events/posters"));
        }
        event.setUserId(userId);

        if (eventMapper.insert(event) != 1) {
            throw new IllegalStateException("이벤트 저장에 실패했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Event getAdminEvent(Long id) {
        return requireEvent(eventMapper.selectEventById(id));
    }

    @Transactional
    public void update(Long id, EventForm form) {
        Event event = requireEvent(eventMapper.selectEventById(id));
        ValidatedEvent validated = validate(form, event);

        applyEditableFields(event, validated, form);
        if (hasUpload(form.getImageFile())) {
            event.setEventImg(fileUploadService.saveFile(form.getImageFile(), "events"));
        }
        if (hasUpload(form.getPosterFile())) {
            event.setPosterImg(fileUploadService.saveFile(form.getPosterFile(), "events/posters"));
        }

        if (eventMapper.updateEvent(event) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public void deleteEventById(Long id) {
        Event event = eventMapper.selectEventById(id);
        if (event != null) {
            deleteUploadedFile(event.getEventImg());
            deleteUploadedFile(event.getPosterImg());

            // 3. DB에서 이벤트 삭제
            eventMapper.deleteEventById(id);
        }
    }


    public List<Event> selectAllEvents() {
        return eventMapper.selectAllEvents();
    }


    // 이벤트 리스트
    public List<Event> getEventsByStatus(String status) {
        if (status == null || "ongoing".equals(status)) return eventMapper.selectOngoingEvents();
        if ("upcoming".equals(status)) return eventMapper.selectUpcomingEvents();
        if ("ended".equals(status)) return eventMapper.selectEndedEvents();
        return eventMapper.selectOngoingEvents();
    }

    //이벤트 상세
    public Event getEventDetail(Long id) {
        return eventMapper.selectEventById(id);
    }

    /**
     * 이벤트 유형별 최종 검증.
     * existing 이 null 이면 신규 등록, 아니면 기존 이벤트 수정이다.
     */
    private ValidatedEvent validate(EventForm form, Event existing) {
        if (form == null) {
            throw new EventValidationException(null, "이벤트 정보를 입력해 주세요.");
        }
        String title = form.getTitle() == null ? "" : form.getTitle().strip();
        form.setTitle(title);
        if (title.isEmpty()) {
            throw new EventValidationException("title", "이벤트 제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new EventValidationException("title", "이벤트 제목은 255자 이하로 입력해 주세요.");
        }
        if (form.getEventType() == null) {
            throw new EventValidationException("eventType", "이벤트 유형을 선택해 주세요.");
        }

        String description = form.getDescription() == null ? "" : form.getDescription().strip();
        form.setDescription(description);
        LocalDate startDate = resolveDate(
                form.getStartYear(), form.getStartMonth(), form.getStartDay(),
                form.getStartDate(), "startDate", "시작일");
        LocalDate endDate = resolveDate(
                form.getEndYear(), form.getEndMonth(), form.getEndDay(),
                form.getEndDate(), "endDate", "종료일");
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        if (endDate.isBefore(startDate)) {
            throw new EventValidationException("endDate", "종료일은 시작일보다 빠를 수 없습니다.");
        }

        validateTypeContent(form, description, existing);
        validateSlideImage(form, existing);
        return new ValidatedEvent(title, description, startDate, endDate);
    }

    /**
     * STANDARD 는 상세 내용이 필수이고 포스터를 사용하지 않는다.
     * INFOGRAPHIC 은 포스터가 필수이고 상세 내용을 요구하지 않는다.
     * 수정일 때는 기존에 저장된 포스터가 있으면 다시 올리지 않아도 된다.
     */
    private void validateTypeContent(EventForm form, String description, Event existing) {
        if (form.getEventType() == EventType.STANDARD) {
            if (description.isEmpty()) {
                throw new EventValidationException(
                        "description", "일반 이벤트는 상세 내용을 입력해 주세요.");
            }
            return;
        }
        boolean hasPoster = hasUpload(form.getPosterFile())
                || (existing != null && hasText(existing.getPosterImg()));
        if (!hasPoster) {
            throw new EventValidationException(
                    "posterFile", "인포그래픽 이벤트는 인포그래픽 이미지를 등록해 주세요.");
        }
    }

    /** 메인 슬라이더는 대표 이미지를 사용하므로 슬라이더 노출을 선택한 경우에만 대표 이미지가 필요하다. */
    private void validateSlideImage(EventForm form, Event existing) {
        if (!form.isSlide()) {
            return;
        }
        boolean hasRepresentative = hasUpload(form.getImageFile())
                || (existing != null && hasText(existing.getEventImg()));
        if (!hasRepresentative) {
            throw new EventValidationException(
                    "imageFile", "메인 슬라이더에 표시하려면 대표 이미지를 선택해 주세요.");
        }
    }

    private boolean hasUpload(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDate resolveDate(String year,
                                  String month,
                                  String day,
                                  LocalDate fallback,
                                  String field,
                                  String label) {
        boolean hasParts = !isBlank(year) || !isBlank(month) || !isBlank(day);
        if (!hasParts) {
            if (fallback == null) {
                throw new EventValidationException(field, label + "을 모두 입력해 주세요.");
            }
            return fallback;
        }
        if (isBlank(year) || isBlank(month) || isBlank(day)) {
            throw new EventValidationException(field, label + "을 모두 입력해 주세요.");
        }

        String normalizedYear = year.strip();
        String normalizedMonth = month.strip();
        String normalizedDay = day.strip();
        if (!normalizedYear.matches("\\d{4}")
                || !normalizedMonth.matches("\\d{1,2}")
                || !normalizedDay.matches("\\d{1,2}")) {
            throw new EventValidationException(field, label + "을 올바른 숫자로 입력해 주세요.");
        }
        try {
            int parsedYear = Integer.parseInt(normalizedYear);
            if (parsedYear < 1) {
                throw new DateTimeException("year");
            }
            return LocalDate.of(
                    parsedYear,
                    Integer.parseInt(normalizedMonth),
                    Integer.parseInt(normalizedDay));
        } catch (DateTimeException | NumberFormatException exception) {
            throw new EventValidationException(field, "실제로 존재하는 " + label + "을 입력해 주세요.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void applyEditableFields(Event event, ValidatedEvent validated, EventForm form) {
        event.setTitle(validated.title());
        event.setDescription(validated.description());
        event.setEventType(form.getEventType());
        event.setSlide(form.isSlide());
        event.setStartDate(validated.startDate());
        event.setEndDate(validated.endDate());
    }

    private Event requireEvent(Event event) {
        if (event == null) {
            throw notFound();
        }
        return event;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다.");
    }

    private void deleteUploadedFile(String imageUrl) {
        if (uploadDir == null || imageUrl == null || !imageUrl.startsWith("/uploads/")) {
            return;
        }
        String relativePath = imageUrl.substring("/uploads/".length());
        File file = new File(uploadDir, relativePath);
        if (file.exists()) {
            file.delete();
        }
    }

    private record ValidatedEvent(String title,
                                  String description,
                                  LocalDate startDate,
                                  LocalDate endDate) {
    }

}
