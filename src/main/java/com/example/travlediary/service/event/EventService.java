package com.example.travlediary.service.event;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.EventForm;
import com.example.travlediary.dto.EventTranslationForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();

    /** 언어별 포스터도 한국어 포스터와 같은 자리에 같은 규칙으로 저장한다. */
    private static final String POSTER_DIRECTORY = "events/posters";

    /** 관리자 탭이 저장할 수 있는 언어. ko 는 base 에서 맞추므로 여기에 없다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .map(SupportedLanguage::getLanguageTag)
            .filter(languageTag -> !KOREAN_CODE.equals(languageTag))
            .collect(Collectors.toUnmodifiableSet());

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
            event.setPosterImg(fileUploadService.saveFile(form.getPosterFile(), POSTER_DIRECTORY));
        }
        event.setUserId(userId);

        if (eventMapper.insert(event) != 1) {
            throw new IllegalStateException("이벤트 저장에 실패했습니다.");
        }
        // base 저장과 같은 트랜잭션에서 번역까지 끝낸다. ko 는 base 값으로 맞춰진다.
        saveTranslations(event, form.getTranslations());
    }

    @Transactional(readOnly = true)
    public Event getAdminEvent(Long id) {
        return requireEvent(eventMapper.selectEventById(id));
    }

    /**
     * 관리자 수정 화면 복원용. 저장된 줄이 있으면 슬롯에 채우고, 없으면 빈 슬롯을 돌려준다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 저장된 줄의 순서나 개수에 기대지 않는다.
     */
    @Transactional(readOnly = true)
    public List<EventTranslationForm> getTranslationForms(Long eventId) {
        List<EventTranslationForm> slots = EventTranslationForm.newTranslationSlots();
        if (eventId == null) {
            return slots;
        }

        Map<String, EventTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (EventTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        List<EventTranslation> stored = eventMapper.findTranslationsByEventId(eventId);
        if (stored != null) {
            for (EventTranslation translation : stored) {
                if (translation == null || translation.getLanguageCode() == null) {
                    continue;
                }
                EventTranslationForm slot = slotsByLanguage.get(translation.getLanguageCode());
                if (slot == null) {
                    // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                    continue;
                }
                slot.setTitle(translation.getTitle() == null ? "" : translation.getTitle());
                slot.setDescription(
                        translation.getDescription() == null ? "" : translation.getDescription());
            }
        }
        return slots;
    }

    /**
     * 관리자 수정 화면의 언어별 포스터 미리보기용. 언어 코드 → 저장된 포스터 경로.
     *
     * <p>경로는 폼에 싣지 않고 화면을 그릴 때마다 DB 에서 다시 읽는다.
     * 저장돼 있지 않은 언어는 아예 담기지 않으므로 화면은 언어 코드로만 찾으면 된다.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getTranslationPosterImages(Long eventId) {
        if (eventId == null) {
            return Map.of();
        }
        List<EventTranslation> stored = eventMapper.findTranslationsByEventId(eventId);
        if (stored == null) {
            return Map.of();
        }
        Map<String, String> posters = new LinkedHashMap<>();
        for (EventTranslation translation : stored) {
            if (translation == null || translation.getLanguageCode() == null) {
                continue;
            }
            String posterImg = nonBlank(translation.getPosterImg());
            if (posterImg != null) {
                posters.putIfAbsent(translation.getLanguageCode(), posterImg);
            }
        }
        return Map.copyOf(posters);
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
            event.setPosterImg(fileUploadService.saveFile(form.getPosterFile(), POSTER_DIRECTORY));
        }

        if (eventMapper.updateEvent(event) != 1) {
            throw notFound();
        }
        // base 저장과 같은 트랜잭션에서 번역까지 끝낸다. ko 는 방금 저장한 base 값으로 맞춰진다.
        saveTranslations(event, form.getTranslations());
    }

    @Transactional
    public void deleteEventById(Long id) {
        Event event = eventMapper.selectEventById(id);
        if (event != null) {
            deleteUploadedFile(event.getEventImg());
            deleteUploadedFile(event.getPosterImg());
            // 언어별 포스터는 줄과 함께 사라지므로(FK ON DELETE CASCADE) 파일도 같이 지운다.
            for (String posterImg : getTranslationPosterImages(id).values()) {
                deleteUploadedFile(posterImg);
            }

            // 3. DB에서 이벤트 삭제
            eventMapper.deleteEventById(id);
        }
    }

    /** 방금 저장한 이벤트를 base 로 삼아 번역을 맞춘다. */
    private void saveTranslations(Event event, List<EventTranslationForm> translationForms) {
        saveTranslations(event.getId(), event.getTitle(), event.getDescription(),
                event.getPosterImg(), translationForms);
    }

    /**
     * 언어별 번역을 base 저장과 같은 트랜잭션에서 맞춘다.
     *
     * <p>한국어 줄은 폼 입력이 아니라 events 원문(title/description/poster_img)을 그대로 따른다.
     * 한국어 포스터의 원본은 언제나 events.poster_img 이고, 번역 탭이 이를 바꾸지 않는다.
     *
     * <p>나머지 언어는 제목·상세 내용·언어별 포스터 중 하나라도 남아 있으면 줄을 남기고,
     * 셋 다 비면 줄을 지운다.
     */
    @Transactional
    public void saveTranslations(Long eventId,
                                 String baseTitle,
                                 String baseDescription,
                                 String basePosterImg,
                                 List<EventTranslationForm> translationForms) {
        if (eventId == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다. 포스터를 지키려면 기존 값이 필요하다.
        Map<String, EventTranslation> existing = new LinkedHashMap<>();
        List<EventTranslation> stored = eventMapper.findTranslationsByEventId(eventId);
        if (stored != null) {
            for (EventTranslation translation : stored) {
                if (translation != null && translation.getLanguageCode() != null) {
                    existing.putIfAbsent(translation.getLanguageCode(), translation);
                }
            }
        }

        saveKoreanTranslation(eventId, baseTitle, baseDescription, basePosterImg,
                existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        Set<String> handledLanguages = new HashSet<>();
        for (EventTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null) {
                continue;
            }
            String languageCode = form.getLanguageCode();
            if (!SUPPORTED_TRANSLATION_CODES.contains(languageCode)) {
                // 화면이 정한 슬롯 언어만 저장한다. ko 슬롯과 임의 언어 코드는 무시한다.
                continue;
            }
            if (!handledLanguages.add(languageCode)) {
                // 같은 언어가 두 번 들어오면 앞의 값만 쓴다. (UNIQUE 충돌을 만들지 않는다)
                continue;
            }
            saveForeignTranslation(eventId, form, existing.get(languageCode));
        }
    }

    /**
     * 한국어 줄은 events 원문을 그대로 비춘다.
     * base 제목·상세 내용은 비어 있을 수 없으므로 이 줄은 언제나 남는다.
     */
    private void saveKoreanTranslation(Long eventId,
                                       String baseTitle,
                                       String baseDescription,
                                       String basePosterImg,
                                       boolean exists) {
        EventTranslation translation = new EventTranslation();
        translation.setEventId(eventId);
        translation.setLanguageCode(KOREAN_CODE);
        translation.setTitle(nonBlank(baseTitle));
        translation.setDescription(nonBlank(baseDescription));
        translation.setPosterImg(nonBlank(basePosterImg));

        if (exists) {
            eventMapper.updateTranslation(translation);
        } else {
            eventMapper.insertTranslation(translation);
        }
    }

    /**
     * 외국어 줄 한 개를 맞춘다. 언어마다 따로 처리하므로 한 언어를 손대도 다른 언어 줄은 그대로다.
     *
     * <p>줄의 생명주기는 최종 세 값(title / description / poster_img)으로 정한다.
     * 셋 다 비면 줄을 지우고, 하나라도 남으면 줄을 남긴다.
     * 그래서 글자가 없어도 언어별 포스터만으로 줄이 생길 수 있다.
     *
     * <p>파일은 다음 순서로 다룬다.
     * <ol>
     *   <li>새 파일이 있으면 먼저 저장한다. (삭제 체크가 함께 와도 새 파일이 이긴다)</li>
     *   <li>DB 를 새 값으로 맞춘다. 실패하면 방금 올린 파일만 지우고 예외를 그대로 올린다.</li>
     *   <li>DB 가 새 값을 가리킨 뒤에야 예전 파일을 지운다.</li>
     * </ol>
     *
     * @param existing 저장돼 있던 같은 언어 줄. 없으면 null.
     *                 예전 포스터 경로는 오직 이 값에서만 읽는다 — 폼이 보낸 경로는 믿지 않는다.
     */
    private void saveForeignTranslation(Long eventId,
                                        EventTranslationForm form,
                                        EventTranslation existing) {
        String title = nonBlank(form.getTitle());
        String description = nonBlank(form.getDescription());
        // 예전 경로는 서버가 읽은 DB 값이다. 이 값만 삭제 대상이 될 수 있다.
        String storedPoster = existing == null ? null : nonBlank(existing.getPosterImg());

        String uploadedPoster = null;
        if (hasUpload(form.getPosterFile())) {
            // 삭제 체크와 새 파일이 함께 오면 새 파일을 쓴다. 저장했다가 곧 지우는 일은 없다.
            uploadedPoster = fileUploadService.saveFile(form.getPosterFile(), POSTER_DIRECTORY);
        }
        String posterImg;
        if (uploadedPoster != null) {
            posterImg = uploadedPoster;
        } else if (form.isRemovePoster()) {
            posterImg = null;
        } else {
            // 파일 칸이 비었다는 이유만으로 저장된 포스터를 지우지 않는다.
            posterImg = storedPoster;
        }

        try {
            if (title == null && description == null && posterImg == null) {
                if (existing == null) {
                    // 값이 없는 언어는 새로 만들지 않는다.
                    return;
                }
                eventMapper.deleteTranslation(eventId, form.getLanguageCode());
            } else {
                EventTranslation translation = new EventTranslation();
                translation.setEventId(eventId);
                translation.setLanguageCode(form.getLanguageCode());
                translation.setTitle(title);
                translation.setDescription(description);
                translation.setPosterImg(posterImg);

                if (existing == null) {
                    eventMapper.insertTranslation(translation);
                } else {
                    eventMapper.updateTranslation(translation);
                }
            }
        } catch (RuntimeException exception) {
            // 저장이 실패하면 방금 올린 파일만 지운다. 예전 파일은 아직 DB 가 가리키고 있다.
            deleteUploadedFile(uploadedPoster);
            throw exception;
        }

        if (storedPoster != null && !storedPoster.equals(posterImg)) {
            // 이 언어 줄에 저장돼 있던 파일만 지운다.
            deleteUploadedFile(storedPoster);
        }
    }

    /** 공백만 있는 입력은 값이 없는 것으로 보고, 저장 값은 base 와 같게 strip 한다. */
    private String nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
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

    /** 상태별 이벤트 한 페이지. 상태 값은 컨트롤러에서 ongoing/upcoming/ended 로 정규화된다. */
    public List<Event> getEventsByStatus(String status, long offset, int size) {
        return eventMapper.selectEventsByStatusPaged(normalizeStatus(status), Math.max(offset, 0), size);
    }

    /** 상태별 전체 개수 (페이지 수 계산용) */
    public long countEventsByStatus(String status) {
        return eventMapper.countEventsByStatus(normalizeStatus(status));
    }

    private String normalizeStatus(String status) {
        return switch (status == null ? "" : status) {
            case "upcoming", "ended" -> status;
            default -> "ongoing";
        };
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
