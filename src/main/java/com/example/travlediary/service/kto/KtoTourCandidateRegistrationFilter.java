package com.example.travlediary.service.kto;

import java.util.Arrays;
import java.util.Locale;

/**
 * 후보 목록의 등록상태 필터.
 * 전체 후보를 만들고 contentId 로 등록 여부를 판정한 뒤 이 필터를 적용하고, 페이징은 그 다음이다.
 * 그래서 특정 TourAPI 페이지에 등록완료 항목이 없다는 이유로 등록완료 탭이 비지 않는다.
 */
public enum KtoTourCandidateRegistrationFilter {

    ALL,
    /** 아직 우리 DB 에 없는 후보. 관리자 화면 기본값이다. */
    NEW,
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

    public boolean accepts(boolean registered) {
        return switch (this) {
            case ALL -> true;
            case NEW -> !registered;
            case REGISTERED -> registered;
        };
    }
}
