package com.example.travlediary.dto;

import com.example.travlediary.model.Diary;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 월간 달력 한 장. 화면이 그대로 그릴 수 있게 주 단위로 미리 나눠 담는다.
 * 다이어리 정보는 기존 {@link Diary} 를 그대로 쓰고 새 구조를 따로 만들지 않는다.
 */
public record DiaryCalendarDto(YearMonth month, List<List<Day>> weeks) {

    /** 연도 고르기에 기본으로 여는 앞뒤 범위 */
    private static final int YEAR_RANGE = 10;

    /** 달력 한 칸. */
    public record Day(LocalDate date,
                      boolean inCurrentMonth,
                      boolean today,
                      List<Diary> diaries) {

        /** 화면에 쓰는 날짜 숫자 */
        public int dayOfMonth() {
            return date.getDayOfMonth();
        }
    }

    /** ‹ 로 갈 이전 달 (예: 2026-07) */
    public YearMonth previousMonth() {
        return month.minusMonths(1);
    }

    /** › 로 갈 다음 달 */
    public YearMonth nextMonth() {
        return month.plusMonths(1);
    }

    /** 주소에 싣는 값 (yyyy-MM) */
    public String monthValue() {
        return month.toString();
    }

    public String previousMonthValue() {
        return previousMonth().toString();
    }

    public String nextMonthValue() {
        return nextMonth().toString();
    }

    /**
     * 연도 고르기 목록. 지금 연도 앞뒤 10년을 두되,
     * 주소로 바로 들어온 연도(예: 1998-03)도 반드시 목록에 들어가게 한다.
     */
    public List<Integer> selectableYears() {
        int now = Year.now().getValue();
        int from = Math.min(now - YEAR_RANGE, month.getYear());
        int to = Math.max(now + YEAR_RANGE, month.getYear());
        return IntStream.rangeClosed(from, to).boxed().toList();
    }

    /** 월 고르기 목록 (1 ~ 12) */
    public List<Integer> selectableMonths() {
        return IntStream.rangeClosed(1, 12).boxed().toList();
    }

    /** 지금 보고 있는 달이 이번 달인지. ('오늘' 을 굳이 강조하지 않기 위해) */
    public boolean isThisMonth() {
        return month.equals(YearMonth.now());
    }

    /** 이 달에 기록이 하나도 없는지 */
    public boolean isEmpty() {
        return weeks.stream().flatMap(List::stream)
                .noneMatch(day -> day.inCurrentMonth() && !day.diaries().isEmpty());
    }
}
