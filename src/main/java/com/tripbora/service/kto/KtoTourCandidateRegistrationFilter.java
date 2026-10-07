package com.tripbora.service.kto;

import com.tripbora.service.destination.DestinationDuplicateStatus;

import java.util.Arrays;
import java.util.Locale;

/**
 * 후보 목록의 등록상태 필터.
 * 전체 후보를 만들고 중복 판별을 한 뒤 이 필터를 적용하고, 페이징은 그 다음이다.
 * 그래서 특정 TourAPI 페이지에 등록완료 항목이 없다는 이유로 등록완료 탭이 비지 않는다.
 */
public enum KtoTourCandidateRegistrationFilter {

    ALL,
    /** 우리 DB 에 같은 곳이 없는 후보. 관리자 화면 기본값이다. 중복 확인 후보는 여기 넣지 않는다. */
    NEW,
    /** 이름·위치가 같은 기존 여행지가 있어 관리자 확인이 필요한 후보. */
    POSSIBLE_DUPLICATE,
    REGISTERED;

    public static KtoTourCandidateRegistrationFilter from(String value) {
        if (value == null || value.isBlank()) {
            return NEW;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(filter -> filter.name().equals(normalized))
                .findFirst()
                .orElse(NEW);
    }

    public boolean accepts(DestinationDuplicateStatus status) {
        return switch (this) {
            case ALL -> true;
            case NEW -> status == DestinationDuplicateStatus.NOT_REGISTERED;
            case POSSIBLE_DUPLICATE -> status == DestinationDuplicateStatus.POSSIBLE_DUPLICATE;
            case REGISTERED -> status == DestinationDuplicateStatus.REGISTERED;
        };
    }
}
