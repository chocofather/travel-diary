package com.example.travlediary.service.travelplan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지금 누가 붙어 있는지만 세는 메모리 장부.
 * 사람 단위가 아니라 연결 단위로 세는 것이 핵심이다(탭 여러 개).
 */
class TravelPlanPresenceServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long MEMBER_A = 11L;
    private static final Long MEMBER_B = 12L;

    private final TravelPlanPresenceService presence = new TravelPlanPresenceService();

    @Test
    void oneConnectionPutsThatMemberOnline() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");

        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_A);
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isEqualTo(1);
    }

    @Test
    void closingOneTabLeavesTheOtherTabOnline() {
        presence.join(PLAN_ID, MEMBER_A, "tab-1");
        presence.join(PLAN_ID, MEMBER_A, "tab-2");
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isEqualTo(2);

        presence.leave("tab-1");

        // 아직 남은 연결이 있으므로 접속 중이다
        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_A);
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isEqualTo(1);

        presence.leave("tab-2");

        // 마지막 연결이 끊겨야 접속이 끊긴 것이 된다
        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isZero();
    }

    @Test
    void theSameConnectionRegisteredTwiceIsStillOneConnection() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");
        presence.join(PLAN_ID, MEMBER_A, "session-1");

        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isEqualTo(1);

        // 한 번만 끊어도 완전히 빠진다
        presence.leave("session-1");
        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
    }

    @Test
    void aConnectionThatMovesToAnotherRoomDoesNotLingerInTheOldOne() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");
        presence.join(OTHER_PLAN_ID, MEMBER_B, "session-1");

        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        assertThat(presence.onlineMemberIds(OTHER_PLAN_ID)).containsExactly(MEMBER_B);
    }

    @Test
    void roomsNeverMixWithEachOther() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");
        presence.join(OTHER_PLAN_ID, MEMBER_B, "session-2");

        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_A);
        assertThat(presence.onlineMemberIds(OTHER_PLAN_ID)).containsExactly(MEMBER_B);

        presence.leave("session-1");

        // 한 방이 비어도 다른 방은 그대로다
        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        assertThat(presence.onlineMemberIds(OTHER_PLAN_ID)).containsExactly(MEMBER_B);
    }

    @Test
    void severalPeopleInOneRoomAreAllListed() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");
        presence.join(PLAN_ID, MEMBER_B, "session-2");

        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_A, MEMBER_B);
    }

    @Test
    void anUnknownDisconnectIsIgnoredRatherThanFailing() {
        // 등록된 적 없는 연결이 끊겼다고 들어와도 오류가 되면 안 된다
        assertThat(presence.leave("never-registered")).isEmpty();
        assertThat(presence.leave(null)).isEmpty();

        presence.join(PLAN_ID, MEMBER_A, "session-1");
        presence.leave("session-1");
        // 두 번째 끊김도 조용히 지나간다
        assertThat(presence.leave("session-1")).isEmpty();
    }

    @Test
    void aDisconnectTellsWhichRoomNeedsToBeNotified() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");

        assertThat(presence.leave("session-1")).contains(PLAN_ID);
    }

    @Test
    void anEmptyRoomJustHasNobodyOnline() {
        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        assertThat(presence.onlineMemberIds(null)).isEmpty();
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isZero();
    }

    @Test
    void incompleteRegistrationsAreIgnored() {
        presence.join(null, MEMBER_A, "session-1");
        presence.join(PLAN_ID, null, "session-2");
        presence.join(PLAN_ID, MEMBER_A, null);

        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
    }

    @Test
    void manyConnectionsArrivingAtOnceAreCountedExactly() throws InterruptedException {
        int connections = 200;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch done = new CountDownLatch(connections);

        for (int index = 0; index < connections; index++) {
            String sessionId = "session-" + index;
            pool.submit(() -> {
                try {
                    presence.join(PLAN_ID, MEMBER_A, sessionId);
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isEqualTo(connections);

        CountDownLatch closed = new CountDownLatch(connections);
        for (int index = 0; index < connections; index++) {
            String sessionId = "session-" + index;
            pool.submit(() -> {
                try {
                    presence.leave(sessionId);
                } finally {
                    closed.countDown();
                }
            });
        }
        assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 전부 끊기면 빈 방으로 남고 찌꺼기가 쌓이지 않는다
        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        assertThat(presence.sessionCount(PLAN_ID, MEMBER_A)).isZero();
    }

    @Test
    void nothingIsEverWrittenToADatabase() {
        // 이 서비스는 저장소를 알지 못한다. 서버가 다시 뜨면 비어 있는 것이 정상이다
        assertThat(TravelPlanPresenceService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .allMatch(java.util.Map.class::isAssignableFrom);
    }

    @Test
    void thePayloadOnlyCarriesRoomLocalIds() {
        presence.join(PLAN_ID, MEMBER_A, "session-1");
        List<Long> online = presence.onlineMemberIds(PLAN_ID);

        var payload = com.example.travlediary.dto.TravelPlanPresenceDto.of(online);

        assertThat(payload.getOnlineMemberIds()).containsExactly(MEMBER_A);
        assertThat(payload.getOnlineCount()).isEqualTo(1);
        // 계정 정보가 들어갈 자리 자체가 없다
        assertThat(com.example.travlediary.dto.TravelPlanPresenceDto.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("onlineMemberIds", "onlineCount");
    }
}
