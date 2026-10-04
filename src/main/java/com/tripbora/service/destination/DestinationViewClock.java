package com.tripbora.service.destination;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 여행지 일별 조회 집계(destination_view_daily)의 '오늘'.
 * DB 서버의 CURDATE()/time_zone 이 아니라 애플리케이션이 Asia/Seoul 기준 날짜를 정해 Mapper 에 넘긴다.
 * 테스트는 고정 Clock 을 넣어 날짜 경계를 확인한다.
 */
@Component
public class DestinationViewClock {

    public static final ZoneId VIEW_DATE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    @Autowired
    public DestinationViewClock() {
        this(Clock.system(VIEW_DATE_ZONE));
    }

    public DestinationViewClock(Clock clock) {
        this.clock = clock;
    }

    /** 지금 시각의 Asia/Seoul 날짜. Clock 의 기본 시간대와 관계없이 KST 로 자른다. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), VIEW_DATE_ZONE);
    }
}
