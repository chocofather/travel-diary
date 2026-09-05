package com.example.travlediary.service.event;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 이벤트 번역을 읽어 공개 화면에 쓸 제목·본문·포스터를 만든다.
 *
 * <p>제목과 본문은 <b>필드마다 따로</b> 대체한다(요청 언어 → 한국어 → 남은 언어 → base events → null).
 * 그래서 en 줄에 제목만 있고 본문이 비어 있으면 제목은 en, 본문은 ko 가 된다.
 * 줄 단위로 통째로 고르지 않으며, zh-CN 과 zh-TW 가 서로 먼저 봐 주는 예외도 두지 않는다 —
 * 남은 언어는 언제나 {@code language_code ASC, id ASC} 순서로 고른다.
 *
 * <p><b>포스터는 규칙이 다르다.</b> 요청 언어 → 한국어 → base events.poster_img → null 까지만 본다.
 * 읽을 수 없는 다른 외국어 포스터를 보여 주는 것보다 한국어 포스터가 낫기 때문에
 * '남은 언어' 단계를 두지 않는다. 두 규칙을 섞지 않도록 대체 함수를 따로 둔다.
 *
 * <p><b>공개 화면 전용이다.</b> 넘겨받은 {@link Event} 나 번역 줄은 건드리지 않고,
 * 표시할 값만 새 {@link EventTranslation} 에 담아 돌려준다.
 * 관리자 경로는 지금처럼 events 원문을 그대로 읽으면 된다.
 */
@Service
@RequiredArgsConstructor
public class EventLocalizationService {

    private final EventMapper eventMapper;

    /**
     * 이벤트 한 건의 표시용 값을 만든다. 번역은 이 안에서 한 번 읽는다.
     *
     * @param baseTitle       events 원문 제목 (마지막 대체 값)
     * @param baseDescription events 원문 본문 (마지막 대체 값)
     * @param basePosterImg   events 원문 포스터 경로 (마지막 대체 값)
     */
    @Transactional(readOnly = true)
    public EventTranslation resolveLocalizedContent(Long eventId,
                                                    String baseTitle,
                                                    String baseDescription,
                                                    String basePosterImg,
                                                    SupportedLanguage requestedLanguage) {
        // 이벤트 번호가 없으면 읽을 번역도 없다. base 만 그대로 태워 보낸다.
        List<EventTranslation> translations = eventId == null
                ? List.of()
                : eventMapper.findTranslationsByEventId(eventId);
        return resolveLocalizedContent(eventId, baseTitle, baseDescription, basePosterImg,
                translations, requestedLanguage);
    }

    /**
     * 번역을 이미 읽어 둔 경우에 쓰는 대체 규칙 본체. 조회를 하지 않는다.
     *
     * <p>넘어온 목록에 다른 이벤트의 줄이 섞여 있어도 해당 이벤트 줄만 본다.
     */
    public EventTranslation resolveLocalizedContent(Long eventId,
                                                    String baseTitle,
                                                    String baseDescription,
                                                    String basePosterImg,
                                                    Collection<EventTranslation> translations,
                                                    SupportedLanguage requestedLanguage) {
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        List<EventTranslation> ordered = orderedTranslations(translations).stream()
                .filter(translation -> Objects.equals(eventId, translation.getEventId()))
                .toList();
        return localize(eventId, language, ordered, baseTitle, baseDescription, basePosterImg);
    }

    /**
     * 목록 화면용. 번역을 <b>한 번의 조회</b>로 모아 읽어 이벤트마다 표시용 값을 만든다.
     *
     * <p>base 값은 넘겨받은 이벤트에서 그대로 읽는다. 이벤트 객체 자체는 바꾸지 않는다.
     *
     * @return 이벤트 번호 → 표시용 값. 볼 이벤트가 없으면 조회 없이 빈 맵.
     */
    @Transactional(readOnly = true)
    public Map<Long, EventTranslation> resolveLocalizedContentByEvents(
            Collection<Event> events,
            SupportedLanguage requestedLanguage) {
        Map<Long, Event> baseEvents = new LinkedHashMap<>();
        if (events != null) {
            for (Event event : events) {
                if (event != null && event.getId() != null) {
                    baseEvents.putIfAbsent(event.getId(), event);
                }
            }
        }
        if (baseEvents.isEmpty()) {
            // 볼 이벤트가 없으면 번역도 읽지 않는다.
            return Map.of();
        }

        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        Map<Long, List<EventTranslation>> translationsByEventId =
                orderedTranslations(eventMapper.findTranslationsByEventIds(
                                List.copyOf(baseEvents.keySet())))
                        .stream()
                        .filter(translation -> translation.getEventId() != null)
                        .collect(Collectors.groupingBy(
                                EventTranslation::getEventId,
                                LinkedHashMap::new,
                                Collectors.toList()));

        Map<Long, EventTranslation> localized = new LinkedHashMap<>();
        for (Map.Entry<Long, Event> entry : baseEvents.entrySet()) {
            Long eventId = entry.getKey();
            Event base = entry.getValue();
            localized.put(eventId, localize(
                    eventId,
                    language,
                    translationsByEventId.getOrDefault(eventId, List.of()),
                    base.getTitle(),
                    base.getDescription(),
                    base.getPosterImg()));
        }
        return Map.copyOf(localized);
    }

    /**
     * 공개 목록용. 표시 값만 바꾼 복사본 목록을 만든다. 번역은 <b>한 번의 조회</b>로 모아 읽는다.
     *
     * <p>넘겨받은 이벤트는 손대지 않는다. 관리자 경로가 같은 객체를 보고 있어도 안전하다.
     */
    @Transactional(readOnly = true)
    public List<Event> localizeAll(List<Event> events, SupportedLanguage requestedLanguage) {
        if (events == null || events.isEmpty()) {
            // 볼 이벤트가 없으면 번역도 읽지 않는다.
            return List.of();
        }
        Map<Long, EventTranslation> localized =
                resolveLocalizedContentByEvents(events, requestedLanguage);
        List<Event> display = new ArrayList<>(events.size());
        for (Event event : events) {
            display.add(display(event, localizedFor(localized, event)));
        }
        return List.copyOf(display);
    }

    /** 공개 상세용. 번역을 한 번 읽어 표시 값만 바꾼 복사본을 만든다. */
    @Transactional(readOnly = true)
    public Event localize(Event event, SupportedLanguage requestedLanguage) {
        if (event == null) {
            return null;
        }
        EventTranslation localized = resolveLocalizedContent(
                event.getId(), event.getTitle(), event.getDescription(), event.getPosterImg(),
                requestedLanguage);
        return display(event, localized);
    }

    private EventTranslation localizedFor(Map<Long, EventTranslation> localized, Event event) {
        if (event == null || event.getId() == null) {
            return null;
        }
        return localized.get(event.getId());
    }

    /**
     * 표시용 복사본. 언어와 무관한 값은 원본을 그대로 옮긴다.
     *
     * <p>대표 이미지(event_img)는 언제나 공용이고, 언어별 포스터는 인포그래픽 이벤트에서만 쓴다.
     * 유형 판단은 원본 event_type 그대로이며 포스터 존재 여부로 추론하지 않는다.
     */
    private Event display(Event base, EventTranslation localized) {
        if (base == null) {
            return null;
        }
        Event display = new Event();
        display.setId(base.getId());
        display.setTitle(localized == null ? base.getTitle() : localized.getTitle());
        display.setDescription(
                localized == null ? base.getDescription() : localized.getDescription());
        display.setEventImg(base.getEventImg());
        display.setPosterImg(displayPosterImg(base, localized));
        display.setEventType(base.getEventType());
        display.setSlide(base.getSlide());
        display.setUserId(base.getUserId());
        display.setStartDate(base.getStartDate());
        display.setEndDate(base.getEndDate());
        display.setCreatedAt(base.getCreatedAt());
        return display;
    }

    /** 일반 이벤트는 언어별 포스터를 쓰지 않으므로 저장된 값을 그대로 둔다. */
    private String displayPosterImg(Event base, EventTranslation localized) {
        if (localized == null || base.getEventType() == EventType.STANDARD) {
            return base.getPosterImg();
        }
        return localized.getPosterImg();
    }

    /** 남은 언어를 고를 때 늘 같은 줄이 나오도록 정렬을 고정한다. */
    public List<EventTranslation> orderedTranslations(Collection<EventTranslation> translations) {
        if (translations == null) {
            return List.of();
        }
        return translations.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(EventTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EventTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public EventTranslation translationFor(List<EventTranslation> translations,
                                           String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private EventTranslation localize(Long eventId,
                                      SupportedLanguage language,
                                      List<EventTranslation> ordered,
                                      String baseTitle,
                                      String baseDescription,
                                      String basePosterImg) {
        EventTranslation requested = translationFor(ordered, language.getLanguageTag());
        EventTranslation korean =
                translationFor(ordered, SupportedLanguage.KOREAN.getLanguageTag());

        EventTranslation display = new EventTranslation();
        display.setEventId(eventId);
        display.setLanguageCode(language.getLanguageTag());
        display.setTitle(localizedText(
                EventTranslation::getTitle, requested, korean, ordered, baseTitle));
        display.setDescription(localizedText(
                EventTranslation::getDescription, requested, korean, ordered, baseDescription));
        display.setPosterImg(localizedPoster(requested, korean, basePosterImg));
        return display;
    }

    /**
     * 글자 한 필드의 대체 순서: 요청 언어 → 한국어 → 남은 언어 → base → null.
     *
     * <p>공백만 있는 값은 없는 값으로 본다.
     */
    private String localizedText(Function<EventTranslation, String> field,
                                 EventTranslation requested,
                                 EventTranslation korean,
                                 List<EventTranslation> ordered,
                                 String baseValue) {
        for (EventTranslation translation : Arrays.asList(requested, korean)) {
            if (translation != null && hasText(field.apply(translation))) {
                return field.apply(translation);
            }
        }
        List<String> remaining = new ArrayList<>();
        for (EventTranslation translation : ordered) {
            remaining.add(field.apply(translation));
        }
        return remaining.stream()
                .filter(EventLocalizationService::hasText)
                .findFirst()
                .orElseGet(() -> hasText(baseValue) ? baseValue : null);
    }

    /**
     * 포스터 대체 순서: 요청 언어 → 한국어 → base events.poster_img → null.
     *
     * <p>글자와 달리 '남은 언어' 단계가 없다. 일본어 화면에 영어 포스터를 띄우는 것보다
     * 한국어 포스터나 원본 포스터를 그대로 보여 주는 편이 낫기 때문이다.
     */
    private String localizedPoster(EventTranslation requested,
                                   EventTranslation korean,
                                   String basePosterImg) {
        for (EventTranslation translation : Arrays.asList(requested, korean)) {
            if (translation != null && hasText(translation.getPosterImg())) {
                return translation.getPosterImg();
            }
        }
        return hasText(basePosterImg) ? basePosterImg : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
