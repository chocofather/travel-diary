package com.example.travlediary.service.holiday;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특일 정보(공휴일 / 24절기 / 잡절)는 달력에 덧붙이는 값일 뿐이라, 못 불러와도 화면이 깨지면 안 된다.
 * (실제 인증키는 환경변수로만 들어오므로 테스트에서는 빈 키로 확인한다)
 */
class HolidayServiceTest {

    private final HolidayService holidayService = new HolidayService("");

    /** 인증키가 없으면 외부 API 를 부르지 않는다. (달력이 스스로 아는 날만 남는다) */
    @Test
    void withoutAnApiKeyItReturnsNoSpecialDays() {
        assertThat(holidayService.findSpecialDays(YearMonth.of(2026, 8))).isEmpty();
        assertThat(holidayService.findSpecialDays(null)).isEmpty();
    }

    /** 크리스마스 이브는 외부 API 에 없어 달력이 직접 적는다. 쉬는 날은 아니다. */
    @Test
    void christmasEveIsAddedByTheCalendarItself() {
        Map<LocalDate, SpecialDays> december = holidayService.findSpecialDays(YearMonth.of(2026, 12));

        SpecialDays eve = december.get(LocalDate.of(2026, 12, 24));
        assertThat(eve).isNotNull();
        assertThat(eve.hasHoliday()).isFalse();
        assertThat(eve.holidayNames()).isEmpty();
        assertThat(eve.termNames()).containsExactly("크리스마스 이브");
        assertThat(eve.all()).containsExactly(
                new SpecialDay("크리스마스 이브", SpecialDay.Kind.LOCAL_DAY));
    }

    /** 어버이날도 같은 로컬 정의로 적는다. 쉬는 날은 아니다. */
    @Test
    void parentsDayIsAddedByTheCalendarItself() {
        Map<LocalDate, SpecialDays> may = holidayService.findSpecialDays(YearMonth.of(2026, 5));

        SpecialDays parentsDay = may.get(LocalDate.of(2026, 5, 8));
        assertThat(parentsDay).isNotNull();
        assertThat(parentsDay.hasHoliday()).isFalse();
        assertThat(parentsDay.termNames()).containsExactly("어버이날");
        assertThat(parentsDay.all()).containsExactly(
                new SpecialDay("어버이날", SpecialDay.Kind.LOCAL_DAY));
    }

    /** 정해 둔 날이 없는 달에는 아무것도 끼어들지 않는다. */
    @Test
    void otherMonthsGetNoLocalDays() {
        assertThat(holidayService.findSpecialDays(YearMonth.of(2026, 11))).isEmpty();
    }

    /** 응답 원문은 그대로 두고 달력에 적는 이름만 바꾼다. */
    @Test
    void familiarNamesAreShownInsteadOfTheRawOnes() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <response><body><items>
                  <item><dateName>기독탄신일</dateName><isHoliday>Y</isHoliday><locdate>20261225</locdate></item>
                </items></body></response>
                """;
        // 특일 정보 응답의 1월 1일 이름은 띄어쓰기 없는 '1월1일' 이다.
        String newYear = """
                <?xml version="1.0" encoding="UTF-8"?>
                <response><body><items>
                  <item><dateName>1월1일</dateName><isHoliday>Y</isHoliday><locdate>20260101</locdate></item>
                </items></body></response>
                """;

        List<HolidayService.Dated> found =
                holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY);

        // 이름만 바뀌고 공휴일 판정(isHoliday=Y)은 그대로다
        assertThat(found).containsExactly(new HolidayService.Dated(LocalDate.of(2026, 12, 25),
                new SpecialDay("크리스마스", SpecialDay.Kind.HOLIDAY)));
        assertThat(found.get(0).value().isHoliday()).isTrue();

        assertThat(holidayService.parse(utf8(newYear), SpecialDay.Kind.HOLIDAY))
                .containsExactly(new HolidayService.Dated(LocalDate.of(2026, 1, 1),
                        new SpecialDay("새해", SpecialDay.Kind.HOLIDAY)));
    }

    /** 정해 둔 이름이 없는 공휴일은 응답에 온 이름 그대로 적는다. */
    @Test
    void otherHolidayNamesAreLeftAsTheyCome() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <response><body><items>
                  <item><dateName>광복절</dateName><isHoliday>Y</isHoliday><locdate>20260815</locdate></item>
                </items></body></response>
                """;

        assertThat(names(holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY)))
                .containsExactly("광복절");
    }

    /** 공휴일 조회는 실제로 쉬는 날만 남긴다. */
    @Test
    void onlyRealHolidaysAreRead() throws Exception {
        String xml = """
                <response><body><items>
                  <item><dateKind>01</dateKind><dateName>광복절</dateName>
                        <isHoliday>Y</isHoliday><locdate>20260815</locdate></item>
                  <item><dateKind>02</dateKind><dateName>말복</dateName>
                        <isHoliday>N</isHoliday><locdate>20260810</locdate></item>
                </items></body></response>
                """;

        List<HolidayService.Dated> found =
                holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(found.get(0).value()).isEqualTo(new SpecialDay("광복절", SpecialDay.Kind.HOLIDAY));
    }

    /**
     * 절기·잡절 응답에는 isHoliday 가 N 이거나 아예 없다.
     * 여기에 공휴일 조건을 걸면 하나도 안 남으므로 종류별로 다르게 읽는지 확인한다.
     */
    @Test
    void seasonalTermsAndSundryDaysAreNotFilteredByIsHoliday() throws Exception {
        String terms = """
                <response><body><items>
                  <item><dateName>입추</dateName><isHoliday>N</isHoliday><locdate>20260807</locdate></item>
                </items></body></response>
                """;
        String sundries = """
                <response><body><items>
                  <item><dateName>말복</dateName><locdate>20260815</locdate></item>
                </items></body></response>
                """;

        assertThat(holidayService.parse(utf8(terms), SpecialDay.Kind.SEASONAL_TERM))
                .containsExactly(new HolidayService.Dated(LocalDate.of(2026, 8, 7),
                        new SpecialDay("입추", SpecialDay.Kind.SEASONAL_TERM)));
        assertThat(holidayService.parse(utf8(sundries), SpecialDay.Kind.SUNDRY_DAY))
                .containsExactly(new HolidayService.Dated(LocalDate.of(2026, 8, 15),
                        new SpecialDay("말복", SpecialDay.Kind.SUNDRY_DAY)));
    }

    /** 같은 날짜에 여러 건이 와도 하나도 잃지 않는다. */
    @Test
    void severalHolidaysOnTheSameDayAreAllKept() throws Exception {
        String xml = """
                <response><body><items>
                  <item><dateName>설날</dateName><isHoliday>Y</isHoliday><locdate>20260217</locdate></item>
                  <item><dateName>임시공휴일</dateName><isHoliday>Y</isHoliday><locdate>20260217</locdate></item>
                </items></body></response>
                """;

        assertThat(names(holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY)))
                .containsExactly("설날", "임시공휴일");
    }

    /** 형식이 어긋난 응답에도 예외로 죽지 않고 읽을 수 있는 것만 남긴다. */
    @Test
    void brokenItemsAreSkipped() throws Exception {
        String xml = """
                <response><body><items>
                  <item><dateName>이름만</dateName><isHoliday>Y</isHoliday><locdate>날짜아님</locdate></item>
                  <item><dateName></dateName><isHoliday>Y</isHoliday><locdate>20260101</locdate></item>
                  <item><dateName>신정</dateName><isHoliday>Y</isHoliday><locdate>20260101</locdate></item>
                </items></body></response>
                """;

        assertThat(names(holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY)))
                .containsExactly("신정");
    }

    @Test
    void emptyOrErrorBodyIsTreatedAsNoSpecialDays() throws Exception {
        assertThat(holidayService.parse(null, SpecialDay.Kind.HOLIDAY)).isEmpty();
        assertThat(holidayService.parse(new byte[0], SpecialDay.Kind.SEASONAL_TERM)).isEmpty();
        assertThat(holidayService.parse(utf8(
                "<response><header><resultCode>30</resultCode></header></response>"),
                SpecialDay.Kind.SUNDRY_DAY)).isEmpty();
    }

    /**
     * 특일명이 깨지지 않는지. XML 선언을 파서가 직접 읽으므로
     * 플랫폼 기본 charset 이나 응답 헤더 기본값(ISO-8859-1)에 좌우되지 않는다.
     */
    @Test
    void specialDayNamesKeepTheirKoreanLetters() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <response><body><items>
                  <item><dateName>광복절</dateName><isHoliday>Y</isHoliday><locdate>20260815</locdate></item>
                  <item><dateName>추석</dateName><isHoliday>Y</isHoliday><locdate>20260925</locdate></item>
                  <item><dateName>개천절</dateName><isHoliday>Y</isHoliday><locdate>20261003</locdate></item>
                </items></body></response>
                """;

        assertThat(names(holidayService.parse(utf8(xml), SpecialDay.Kind.HOLIDAY)))
                .containsExactly("광복절", "추석", "개천절");
    }

    /** 하루에 공휴일과 절기가 겹쳐도 화면이 둘을 나눠 쓸 수 있어야 한다. */
    @Test
    void aDayCanHoldBothAHolidayAndATerm() {
        SpecialDays day = new SpecialDays(List.of(
                new SpecialDay("광복절", SpecialDay.Kind.HOLIDAY),
                new SpecialDay("입추", SpecialDay.Kind.SEASONAL_TERM),
                new SpecialDay("말복", SpecialDay.Kind.SUNDRY_DAY)));

        assertThat(day.hasHoliday()).isTrue();
        assertThat(day.holidayNames()).containsExactly("광복절");
        assertThat(day.termNames()).containsExactly("입추", "말복");
    }

    /** 절기·잡절만 있는 날은 빨간 날이 아니다. */
    @Test
    void aDayWithOnlyTermsIsNotAHoliday() {
        SpecialDays day = new SpecialDays(List.of(
                new SpecialDay("입추", SpecialDay.Kind.SEASONAL_TERM)));

        assertThat(day.hasHoliday()).isFalse();
        assertThat(day.holidayNames()).isEmpty();
        assertThat(day.termNames()).containsExactly("입추");
    }

    private List<String> names(List<HolidayService.Dated> found) {
        return found.stream().map(dated -> dated.value().name()).toList();
    }

    /** 응답은 언제나 바이트로 받아 XML 선언대로 읽는다. (플랫폼 기본 charset 에 기대지 않는다) */
    private byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
