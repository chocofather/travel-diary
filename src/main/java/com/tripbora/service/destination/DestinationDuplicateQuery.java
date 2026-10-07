package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.DestinationTranslationForm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 중복 판별할 후보 한 건. 값은 모두 선택이며, 있는 값만으로 판별한다.
 *
 * @param sourceType        외부 출처(KTO_TOURAPI·WIKIDATA). 관리자 직접 입력이면 null
 * @param externalContentId 그 출처의 ID(contentId·QID)
 * @param names             언어와 상관없이 후보의 이름들
 * @param regionId          country_categories 지역. 모르면 null
 */
public record DestinationDuplicateQuery(
        String sourceType,
        String externalContentId,
        String googlePlaceId,
        List<String> names,
        Long regionId,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public DestinationDuplicateQuery {
        names = names == null ? List.of() : names.stream().filter(Objects::nonNull).toList();
    }

    /** 저장 직전 확인용. 등록폼에 담긴 이름(모든 언어)·지역·좌표·Place ID로 만든다. */
    public static DestinationDuplicateQuery fromForm(DestinationForm form, String sourceType,
                                                     String externalContentId) {
        List<String> names = form.getTranslations() == null ? List.of() : form.getTranslations().stream()
                .filter(Objects::nonNull)
                .map(DestinationTranslationForm::getName)
                .toList();
        return new DestinationDuplicateQuery(sourceType, externalContentId, form.getGooglePlaceId(), names,
                form.getRegionId(), form.getLatitude(), form.getLongitude());
    }
}
