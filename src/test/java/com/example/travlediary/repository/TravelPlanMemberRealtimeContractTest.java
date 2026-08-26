package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참여자 명단이 새로고침 없이 맞춰지는 길의 계약.
 *
 * <p>화면은 사람 수를 더하거나 빼지 않는다.
 * "바뀌었다" 는 신호를 받고 서버에서 최신 명단을 다시 읽을 뿐이라,
 * 같은 알림을 두 번 받아도 숫자가 어긋나지 않는다.
 */
class TravelPlanMemberRealtimeContractTest {

    @Test
    void theScreenListensOnTheConnectionItAlreadyHas() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        assertThat(realtime)
                .contains("client.subscribe(`/topic/travel-plans/${planId}/members`")
                .contains("refreshMembers()");
        // 연결은 이 파일 하나가 들고 있다. 명단 때문에 새로 만들지 않는다
        assertThat(countOf(realtime, "new StompJs.Client(")).isEqualTo(1);
        for (String other : new String[]{
                "/static/js/travel-plan-scheduler.js",
                "/static/js/travel-plan-chat.js",
                "/static/js/travel-plan-poll.js",
                "/static/js/travel-plan-finalize.js"}) {
            assertThat(resource(other)).as("%s", other)
                    .doesNotContain("new StompJs.Client")
                    .doesNotContain("new WebSocket");
        }
    }

    @Test
    void theHeadcountIsNeverGuessedOnTheScreen() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");
        String refresh = between(realtime, "async function refreshMembers()",
                "// ── 일정 갱신");

        // 서버에서 다시 읽는다
        assertThat(refresh).contains("/members/fragment");
        // 토글 글자도 서버가 만들어 보낸 값을 그대로 쓴다
        assertThat(refresh).contains("data-members-label");
        // 화면에서 세거나 더하지 않는다
        for (String guess : new String[]{
                "memberCount +", "count++", "count--", "+ 1", "- 1", "length + "}) {
            assertThat(refresh).as("화면이 숫자를 지어냄: %s", guess).doesNotContain(guess);
        }
    }

    @Test
    void aLateAnswerDoesNotOverwriteANewerList() throws IOException {
        String refresh = between(resource("/static/js/travel-plan-realtime.js"),
                "async function refreshMembers()", "// ── 일정 갱신");

        // 빠르게 여러 번 바뀌면 응답이 뒤섞여 도착할 수 있다
        assertThat(refresh)
                .contains("++membersRequest")
                .contains("if (membersRequest !== sequence) return");
    }

    @Test
    void anOpenPopoverStaysOpenWhileItsContentChanges() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");
        String refresh = between(realtime, "async function refreshMembers()", "// ── 일정 갱신");

        // 패널 자체는 그대로 두고 속만 갈아 끼운다
        assertThat(refresh).contains("panel.innerHTML = html");
        assertThat(refresh).doesNotContain("panel.replaceWith").doesNotContain("panel.remove()");

        // 새로 그린 줄에 ⋯ 동작이 다시 붙는다 (줄마다 붙여 두면 새 줄에서 눌리지 않는다)
        String scheduler = resource("/static/js/travel-plan-scheduler.js");
        assertThat(scheduler)
                .contains("data-travel-plan-members-panel")
                .contains("event.target.closest(\"[data-travel-plan-member-menu-button]\")")
                .doesNotContain("memberMenus.forEach(menu => {\n        const button");
    }

    @Test
    void theOnlineDotsAreReappliedRatherThanLost() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        /*
          참여자 줄이 새로 그려지면 접속 표시가 없는 상태로 돌아간다.
          지금 알고 있는 접속 상황을 다시 입혀야 점이 사라지지 않는다.
        */
        assertThat(realtime)
                .contains("let lastPresence")
                .contains("renderPresence(lastPresence.onlineMemberIds, lastPresence.onlineCount)");
        // 처음의 목록을 들고 있으면 새로 그린 뒤 화면에 없는 옛 줄에만 표시하게 된다
        assertThat(realtime)
                .contains("document.querySelectorAll(\"[data-travel-plan-member-row]\")")
                .doesNotContain("const rows = document.querySelectorAll");
    }

    @Test
    void theTwoNumbersAreNeverMixedUp() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        /*
          "접속 중 N명" 은 지금 창을 열어 둔 사람 수고,
          "참여자 N/8" 은 방에 속한 사람 수다. topic 도 표시 자리도 다르다.
        */
        assertThat(realtime)
                .contains("/presence`")
                .contains("/members`");
        String presenceRender = between(realtime, "function renderPresence(",
                "// ── 참여자 명단");
        assertThat(presenceRender)
                .contains("data-travel-plan-online-count")
                .doesNotContain("data-travel-plan-members-toggle")
                .doesNotContain("data-members-label");
    }

    @Test
    void aReconnectionCatchesUpOnWhatItMissed() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");
        String reconnect = between(realtime, "if (connectedBefore) {", "connectedBefore = true;");

        // 끊겨 있던 사이의 알림은 다시 받을 수 없다
        assertThat(reconnect).contains("refreshMembers()");
    }

    @Test
    void everyMembershipChangeUsesTheOneNotice() throws IOException {
        String invitation = service("TravelPlanInvitationService");
        String member = service("TravelPlanMemberService");

        // 초대 참여 / 재참여 / 나가기 / 내보내기 / 방장 넘기기
        assertThat(countOf(invitation, "new TravelPlanMembershipChangedEvent(")).isEqualTo(2);
        assertThat(countOf(member, "new TravelPlanMembershipChangedEvent(")).isEqualTo(3);

        // 재참여 허용은 ACTIVE 인원이 그대로라 알리지 않는다
        assertThat(between(member, "public void allowRejoin(", "public void transferOwnership("))
                .doesNotContain("TravelPlanMembershipChangedEvent");

        // Service 는 WebSocket 을 직접 부르지 않는다
        for (String source : new String[]{invitation, member}) {
            assertThat(source)
                    .doesNotContain("SimpMessagingTemplate")
                    .doesNotContain("convertAndSend");
        }
    }

    @Test
    void theRefreshOnlyReadsWhatItNeeds() throws IOException {
        String service = service("TravelPlanService");
        String members = between(service, "public TravelPlanMembersDto getActivePlanMembers(",
                "public TravelPlanAccessNotice explainInaccessiblePlan(");

        // 접근 권한은 상세와 같은 길로 확인한다 (비참여자는 404)
        assertThat(members).contains("requireActiveAccess(userId, travelPlanId)");
        // 사람 하나 들어왔다고 방의 일정 전체를 다시 읽지 않는다
        assertThat(members)
                .doesNotContain("findDaysByPlanId")
                .doesNotContain("travelPlanItemMapper")
                .doesNotContain("travelPlanAlternativeMapper");
    }

    private String service(String name) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + name + ".java"),
                StandardCharsets.UTF_8);
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
