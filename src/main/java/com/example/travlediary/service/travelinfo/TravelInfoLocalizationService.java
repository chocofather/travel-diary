package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 여행정보 번역을 읽어 공개 화면에 쓸 제목·본문을 만든다.
 *
 * <p>언어 대체는 <b>필드마다 따로</b> 적용한다(요청 언어 → 한국어 → 남은 언어 → base → null).
 * 그래서 en 줄에 제목만 있고 본문이 비어 있으면 제목은 en, 본문은 ko 가 된다.
 * zh-CN 과 zh-TW 를 서로 먼저 봐 주는 예외는 두지 않는다 — 남은 언어는 언제나
 * {@code language_code ASC, id ASC} 순서로 고른다.
 *
 * <p><b>공개 화면 전용이다.</b> 넘겨받은 base 값이나 관리자 화면이 쓰는 원본 객체는
 * 건드리지 않고, 표시할 값만 새 {@link TravelInfoTranslation} 에 담아 돌려준다.
 * 관리자 경로는 지금처럼 travel_info 원문을 그대로 읽으면 된다.
 */
@Service
@RequiredArgsConstructor
public class TravelInfoLocalizationService {

    private final TravelInfoMapper travelInfoMapper;

    /**
     * 여행정보 한 건의 표시용 제목·본문을 만든다. 번역은 이 안에서 한 번 읽는다.
     *
     * @param baseTitle   travel_info 원문 제목 (마지막 대체 값)
     * @param baseContent travel_info 원문 본문 (마지막 대체 값)
     */
    @Transactional(readOnly = true)
    public TravelInfoTranslation resolveLocalizedContent(Long travelInfoId,
                                                         String baseTitle,
                                                         String baseContent,
                                                         SupportedLanguage requestedLanguage) {
        // 여행정보 번호가 없으면 읽을 번역도 없다. base 만 그대로 태워 보낸다.
        List<TravelInfoTranslation> translations = travelInfoId == null
                ? List.of()
                : travelInfoMapper.findTranslationsByInfoId(travelInfoId);
        return resolveLocalizedContent(travelInfoId, baseTitle, baseContent,
                translations, requestedLanguage);
    }

    /**
     * 번역을 이미 읽어 둔 경우에 쓰는 대체 규칙 본체. 조회를 하지 않는다.
     *
     * <p>넘어온 목록에 다른 여행정보의 줄이 섞여 있어도 해당 여행정보 줄만 본다.
     */
    public TravelInfoTranslation resolveLocalizedContent(
            Long travelInfoId,
            String baseTitle,
            String baseContent,
            Collection<TravelInfoTranslation> translations,
            SupportedLanguage requestedLanguage) {
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        List<TravelInfoTranslation> ordered = orderedTranslations(translations).stream()
                .filter(translation -> Objects.equals(travelInfoId, translation.getTravelInfoId()))
                .toList();
        return localize(travelInfoId, language, ordered, baseTitle, baseContent);
    }

    /**
     * 목록 화면용. 번역을 <b>한 번의 조회</b>로 모아 읽어 여행정보마다 표시용 값을 만든다.
     *
     * @param baseTitles   여행정보 번호 → 원문 제목. 여기 담긴 번호가 조회 대상이다.
     * @param baseContents 여행정보 번호 → 원문 본문. 본문을 쓰지 않는 목록은 비워서 넘긴다.
     */
    @Transactional(readOnly = true)
    public Map<Long, TravelInfoTranslation> resolveLocalizedContentByInfoIds(
            Map<Long, String> baseTitles,
            Map<Long, String> baseContents,
            SupportedLanguage requestedLanguage) {
        Set<Long> infoIds = new LinkedHashSet<>();
        if (baseTitles != null) {
            baseTitles.keySet().stream().filter(Objects::nonNull).forEach(infoIds::add);
        }
        if (baseContents != null) {
            baseContents.keySet().stream().filter(Objects::nonNull).forEach(infoIds::add);
        }
        if (infoIds.isEmpty()) {
            // 볼 여행정보가 없으면 번역도 읽지 않는다.
            return Map.of();
        }

        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        Map<Long, List<TravelInfoTranslation>> translationsByInfoId =
                orderedTranslations(travelInfoMapper.findTranslationsByInfoIds(List.copyOf(infoIds)))
                        .stream()
                        .filter(translation -> translation.getTravelInfoId() != null)
                        .collect(Collectors.groupingBy(
                                TravelInfoTranslation::getTravelInfoId,
                                LinkedHashMap::new,
                                Collectors.toList()));

        Map<Long, TravelInfoTranslation> localized = new LinkedHashMap<>();
        for (Long infoId : infoIds) {
            localized.put(infoId, localize(
                    infoId,
                    language,
                    translationsByInfoId.getOrDefault(infoId, List.of()),
                    baseTitles == null ? null : baseTitles.get(infoId),
                    baseContents == null ? null : baseContents.get(infoId)));
        }
        return Map.copyOf(localized);
    }

    /** 남은 언어를 고를 때 늘 같은 줄이 나오도록 정렬을 고정한다. */
    public List<TravelInfoTranslation> orderedTranslations(
            Collection<TravelInfoTranslation> translations) {
        if (translations == null) {
            return List.of();
        }
        return translations.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(TravelInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TravelInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public TravelInfoTranslation translationFor(List<TravelInfoTranslation> translations,
                                                String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private TravelInfoTranslation localize(Long travelInfoId,
                                           SupportedLanguage language,
                                           List<TravelInfoTranslation> ordered,
                                           String baseTitle,
                                           String baseContent) {
        TravelInfoTranslation requested = translationFor(ordered, language.getLanguageTag());
        TravelInfoTranslation korean =
                translationFor(ordered, SupportedLanguage.KOREAN.getLanguageTag());

        TravelInfoTranslation display = display(travelInfoId, language, null, null);
        display.setTitle(localizedField(TravelInfoTranslation::getTitle,
                TravelInfoLocalizationService::hasText, requested, korean, ordered, baseTitle));
        display.setContent(localizedField(TravelInfoTranslation::getContent,
                TravelInfoContent::hasContent, requested, korean, ordered, baseContent));
        return display;
    }

    /**
     * 한 필드의 대체 순서: 요청 언어 → 한국어 → 남은 언어 → base → null.
     *
     * <p>{@code hasValue} 는 필드마다 다르다. 제목은 공백만 있으면 없는 값으로 보고,
     * 본문은 {@code <p><br></p>} 처럼 태그만 남은 Quill HTML 도 없는 값으로 본다.
     */
    private String localizedField(Function<TravelInfoTranslation, String> field,
                                  Predicate<String> hasValue,
                                  TravelInfoTranslation requested,
                                  TravelInfoTranslation korean,
                                  List<TravelInfoTranslation> ordered,
                                  String baseValue) {
        for (TravelInfoTranslation translation : Arrays.asList(requested, korean)) {
            if (translation != null) {
                String value = field.apply(translation);
                if (hasValue.test(value)) {
                    return value;
                }
            }
        }
        List<String> remaining = new ArrayList<>();
        for (TravelInfoTranslation translation : ordered) {
            remaining.add(field.apply(translation));
        }
        return remaining.stream()
                .filter(hasValue)
                .findFirst()
                .orElseGet(() -> hasValue.test(baseValue) ? baseValue : null);
    }

    private TravelInfoTranslation display(Long travelInfoId,
                                          SupportedLanguage language,
                                          String title,
                                          String content) {
        TravelInfoTranslation display = new TravelInfoTranslation();
        display.setTravelInfoId(travelInfoId);
        display.setLanguageCode((language == null ? SupportedLanguage.KOREAN : language)
                .getLanguageTag());
        display.setTitle(title);
        display.setContent(content);
        return display;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
