package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.stream.Collectors;

/**
 * 축제·행사 상세정보 번역을 읽어 공개 화면에 쓸 값을 만든다.
 *
 * <p>언어 대체는 <b>필드마다 따로</b> 적용한다(요청 언어 → 한국어 → 남은 언어 → base → null).
 * 그래서 en 줄에 장소만 있고 이용요금이 비어 있으면 장소는 en, 이용요금은 ko 가 된다.
 * zh-CN 과 zh-TW 를 서로 먼저 봐 주는 예외는 두지 않는다 — 남은 언어는 언제나
 * {@code language_code ASC, id ASC} 순서로 고른다.
 *
 * <p><b>공개 화면 전용이다.</b> 넘겨받은 {@link FestivalInfo} 원본이나 번역 줄은 건드리지 않고,
 * 표시할 값만 새 {@link FestivalInfoTranslation} 에 담아 돌려준다.
 * 연락처·홈페이지·TourAPI 식별자는 언어와 무관하므로 여기서 다루지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FestivalInfoLocalizationService {

    private final FestivalInfoMapper festivalInfoMapper;

    /**
     * 축제 한 건의 표시용 상세정보를 만든다. 번역은 이 안에서 한 번 읽는다.
     *
     * @param base travel_info 에 딸린 festival_info 원문 (마지막 대체 값)
     * @return 표시용 값. base 가 없으면 null 을 돌려준다.
     */
    @Transactional(readOnly = true)
    public FestivalInfoTranslation resolveLocalizedInfo(FestivalInfo base,
                                                        SupportedLanguage requestedLanguage) {
        if (base == null || base.getInfoId() == null) {
            return null;
        }
        return resolveLocalizedInfo(base,
                festivalInfoMapper.findTranslationsByInfoId(base.getInfoId()), requestedLanguage);
    }

    /**
     * 번역을 이미 읽어 둔 경우에 쓰는 대체 규칙 본체. 조회를 하지 않는다.
     *
     * <p>넘어온 목록에 다른 축제의 줄이 섞여 있어도 해당 축제 줄만 본다.
     */
    public FestivalInfoTranslation resolveLocalizedInfo(
            FestivalInfo base,
            Collection<FestivalInfoTranslation> translations,
            SupportedLanguage requestedLanguage) {
        if (base == null || base.getInfoId() == null) {
            return null;
        }
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        List<FestivalInfoTranslation> ordered = orderedTranslations(translations).stream()
                .filter(translation -> Objects.equals(base.getInfoId(), translation.getInfoId()))
                .toList();
        return localize(base, language, ordered);
    }

    /**
     * 여러 축제를 한 번에 볼 때 쓴다. 번역을 <b>한 번의 조회</b>로 모아 읽는다.
     *
     * @param bases festival_info 원문 목록. 여기 담긴 축제 번호가 조회 대상이다.
     */
    @Transactional(readOnly = true)
    public Map<Long, FestivalInfoTranslation> resolveLocalizedInfoByInfoIds(
            Collection<FestivalInfo> bases,
            SupportedLanguage requestedLanguage) {
        Map<Long, FestivalInfo> basesByInfoId = new LinkedHashMap<>();
        if (bases != null) {
            for (FestivalInfo base : bases) {
                if (base != null && base.getInfoId() != null) {
                    basesByInfoId.putIfAbsent(base.getInfoId(), base);
                }
            }
        }
        if (basesByInfoId.isEmpty()) {
            // 볼 축제가 없으면 번역도 읽지 않는다.
            return Map.of();
        }

        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        Set<Long> infoIds = new LinkedHashSet<>(basesByInfoId.keySet());
        Map<Long, List<FestivalInfoTranslation>> translationsByInfoId =
                orderedTranslations(festivalInfoMapper.findTranslationsByInfoIds(
                        List.copyOf(infoIds)))
                        .stream()
                        .filter(translation -> translation.getInfoId() != null)
                        .collect(Collectors.groupingBy(
                                FestivalInfoTranslation::getInfoId,
                                LinkedHashMap::new,
                                Collectors.toList()));

        Map<Long, FestivalInfoTranslation> localized = new LinkedHashMap<>();
        basesByInfoId.forEach((infoId, base) -> localized.put(infoId, localize(
                base, language, translationsByInfoId.getOrDefault(infoId, List.of()))));
        return Map.copyOf(localized);
    }

    /** 남은 언어를 고를 때 늘 같은 줄이 나오도록 정렬을 고정한다. */
    public List<FestivalInfoTranslation> orderedTranslations(
            Collection<FestivalInfoTranslation> translations) {
        if (translations == null) {
            return List.of();
        }
        return translations.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(FestivalInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FestivalInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public FestivalInfoTranslation translationFor(List<FestivalInfoTranslation> translations,
                                                  String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private FestivalInfoTranslation localize(FestivalInfo base,
                                             SupportedLanguage language,
                                             List<FestivalInfoTranslation> ordered) {
        FestivalInfoTranslation requested = translationFor(ordered, language.getLanguageTag());
        FestivalInfoTranslation korean =
                translationFor(ordered, SupportedLanguage.KOREAN.getLanguageTag());

        FestivalInfoTranslation display = new FestivalInfoTranslation();
        display.setInfoId(base.getInfoId());
        display.setLanguageCode(language.getLanguageTag());
        display.setEventPlace(localizedField(FestivalInfoTranslation::getEventPlace,
                requested, korean, ordered, base.getEventPlace()));
        display.setAddress(localizedField(FestivalInfoTranslation::getAddress,
                requested, korean, ordered, base.getAddress()));
        display.setPlayTime(localizedField(FestivalInfoTranslation::getPlayTime,
                requested, korean, ordered, base.getPlayTime()));
        display.setUseTime(localizedField(FestivalInfoTranslation::getUseTime,
                requested, korean, ordered, base.getUseTime()));
        display.setSponsor1(localizedField(FestivalInfoTranslation::getSponsor1,
                requested, korean, ordered, base.getSponsor1()));
        display.setSponsor2(localizedField(FestivalInfoTranslation::getSponsor2,
                requested, korean, ordered, base.getSponsor2()));
        return display;
    }

    /**
     * 한 필드의 대체 순서: 요청 언어 → 한국어 → 남은 언어 → base → null.
     *
     * <p>번역 칸이 공백만 있으면 값이 없는 것으로 보고 다음으로 넘어간다.
     * 마지막에 base 까지 보므로, 번역이 하나도 없어도 원문에 값이 있으면 그대로 남는다.
     */
    private String localizedField(Function<FestivalInfoTranslation, String> field,
                                  FestivalInfoTranslation requested,
                                  FestivalInfoTranslation korean,
                                  List<FestivalInfoTranslation> ordered,
                                  String baseValue) {
        for (FestivalInfoTranslation translation : Arrays.asList(requested, korean)) {
            if (translation != null && hasText(field.apply(translation))) {
                return field.apply(translation);
            }
        }
        return ordered.stream()
                .map(field)
                .filter(FestivalInfoLocalizationService::hasText)
                .findFirst()
                .orElseGet(() -> hasText(baseValue) ? baseValue : null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
