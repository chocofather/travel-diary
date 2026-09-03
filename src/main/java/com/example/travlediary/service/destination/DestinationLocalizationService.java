package com.example.travlediary.service.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.destination.DestinationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
 * 여행지 번역을 한 번에 모아 읽고, 언어 대체 규칙(요청 언어 → 한국어 → 남은 언어)을 적용한다.
 *
 * <p>여행지 화면뿐 아니라 여행 코스 STOP 처럼 여행지 이름을 빌려 쓰는 곳에서도 같은 규칙을
 * 쓰도록 {@link DestinationService} 에서 떼어 두었다. 여행지 서비스는 코스 서비스를 이미
 * 쓰고 있어, 코스 쪽에서 여행지 서비스를 다시 부르면 서로 물고 도는 참조가 된다.
 */
@Service
@RequiredArgsConstructor
public class DestinationLocalizationService {

    private final DestinationMapper destinationMapper;

    /**
     * 여행지 번호들을 한 번의 조회로 읽어 화면에 쓸 이름·짧은 소개를 만든다.
     *
     * <p>번역이 아예 없는 여행지는 값이 비어 있는 채로 담기므로, 부르는 쪽에서 원래 값을 쓰면 된다.
     */
    public Map<Long, DestinationTranslation> resolveLocalizedContentByDestinationIds(
            Collection<Long> destinationIds,
            SupportedLanguage requestedLanguage) {
        List<Long> ids = destinationIds == null
                ? List.of()
                : destinationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<DestinationTranslation> ordered = orderedTranslations(
                destinationMapper.findTranslationsByDestinationIds(ids));
        Map<Long, List<DestinationTranslation>> translationsByDestinationId = ordered.stream()
                .filter(translation -> translation.getDestinationId() != null)
                .collect(Collectors.groupingBy(
                        DestinationTranslation::getDestinationId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        Map<Long, DestinationTranslation> localized = new LinkedHashMap<>();
        for (Long id : ids) {
            List<DestinationTranslation> translations =
                    translationsByDestinationId.getOrDefault(id, List.of());
            DestinationTranslation requested = translationFor(
                    translations, language.getLanguageTag());
            DestinationTranslation korean = translationFor(
                    translations, SupportedLanguage.KOREAN.getLanguageTag());
            DestinationTranslation display = new DestinationTranslation();
            display.setDestinationId(id);
            display.setLanguageCode(language.getLanguageTag());
            display.setName(localizedField(
                    DestinationTranslation::getName, requested, korean, translations));
            display.setShortDescription(localizedField(
                    DestinationTranslation::getShortDescription,
                    requested, korean, translations));
            localized.put(id, display);
        }
        return Map.copyOf(localized);
    }

    public List<DestinationTranslation> orderedTranslations(
            Collection<DestinationTranslation> translations) {
        if (translations == null) {
            return List.of();
        }
        return translations.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(DestinationTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DestinationTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public DestinationTranslation translationFor(List<DestinationTranslation> translations,
                                                 String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    public String localizedField(Function<DestinationTranslation, String> field,
                                 DestinationTranslation requested,
                                 DestinationTranslation korean,
                                 List<DestinationTranslation> ordered) {
        for (DestinationTranslation translation : Arrays.asList(requested, korean)) {
            if (translation != null) {
                String value = field.apply(translation);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return ordered.stream()
                .map(field)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
