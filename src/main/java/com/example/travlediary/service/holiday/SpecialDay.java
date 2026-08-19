package com.example.travlediary.service.holiday;

/**
 * 특일 정보 한 건. (한국천문연구원 특일 정보)
 * 달력에서 공휴일과 절기/잡절을 다르게 보여야 하므로 종류만 함께 들고 다닌다.
 */
public record SpecialDay(String name, Kind kind) {

    public enum Kind {
        /** 공휴일 (날짜를 빨갛게 표시한다) */
        HOLIDAY,
        /** 24절기 (입춘·하지·동지 …) */
        SEASONAL_TERM,
        /** 잡절 (초복·중복·말복 …) */
        SUNDRY_DAY
    }

    public boolean isHoliday() {
        return kind == Kind.HOLIDAY;
    }
}
