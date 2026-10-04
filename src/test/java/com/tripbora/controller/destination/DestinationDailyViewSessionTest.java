package com.tripbora.controller.destination;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일별 조회 집계의 세션 중복 판정: 같은 세션·같은 KST 날짜·같은 여행지는 한 번만 '처음'이다.
 */
class DestinationDailyViewSessionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Test
    void sameSessionSameDaySameDestinationIsFirstOnlyOnce() {
        MockHttpSession session = new MockHttpSession();

        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, TODAY)).isTrue();
        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, TODAY)).isFalse();
        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, TODAY)).isFalse();
    }

    @Test
    void differentDestinationsAreEachCountedOnce() {
        MockHttpSession session = new MockHttpSession();

        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, TODAY)).isTrue();
        assertThat(DestinationDailyViewSession.markFirstView(session, 8L, TODAY)).isTrue();
        assertThat(DestinationDailyViewSession.markFirstView(session, 8L, TODAY)).isFalse();
    }

    @Test
    void theSameDestinationCountsAgainOnTheNextKstDayAndOnlyTodayIsKept() {
        MockHttpSession session = new MockHttpSession();
        DestinationDailyViewSession.markFirstView(session, 7L, TODAY);
        DestinationDailyViewSession.markFirstView(session, 8L, TODAY);

        LocalDate tomorrow = TODAY.plusDays(1);
        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, tomorrow)).isTrue();
        assertThat(DestinationDailyViewSession.markFirstView(session, 7L, tomorrow)).isFalse();

        // 날짜가 바뀌면 전날 묶음은 버린다. 세션에는 오늘 날짜와 오늘 본 여행지만 남는다.
        var viewed = (DestinationDailyViewSession.ViewedToday)
                session.getAttribute(DestinationDailyViewSession.ATTRIBUTE);
        assertThat(viewed.date()).isEqualTo(tomorrow);
        assertThat(viewed.destinationIds()).containsExactly(7L);
    }

    @Test
    void separateSessionsAreCountedSeparately() {
        assertThat(DestinationDailyViewSession.markFirstView(new MockHttpSession(), 7L, TODAY)).isTrue();
        assertThat(DestinationDailyViewSession.markFirstView(new MockHttpSession(), 7L, TODAY)).isTrue();
    }
}
