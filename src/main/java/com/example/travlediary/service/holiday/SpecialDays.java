package com.example.travlediary.service.holiday;

import java.util.List;

/**
 * 하루치 특일 정보. 같은 날짜에 공휴일과 절기/잡절이 함께 있을 수 있어 목록으로 담는다.
 * 화면이 바로 쓸 수 있게 종류별 이름만 꺼내 준다.
 */
public record SpecialDays(List<SpecialDay> all) {

    /** 공휴일이 있는 날인지. (날짜 숫자를 빨갛게 할지 정한다) */
    public boolean hasHoliday() {
        return all.stream().anyMatch(SpecialDay::isHoliday);
    }

    /** 공휴일 이름 (예: 광복절, 대체공휴일) */
    public List<String> holidayNames() {
        return all.stream().filter(SpecialDay::isHoliday).map(SpecialDay::name).toList();
    }

    /** 절기·잡절 이름 (예: 입추, 말복). 화면에서는 같은 모양으로 옅게 적는다. */
    public List<String> termNames() {
        return all.stream().filter(day -> !day.isHoliday()).map(SpecialDay::name).toList();
    }
}
