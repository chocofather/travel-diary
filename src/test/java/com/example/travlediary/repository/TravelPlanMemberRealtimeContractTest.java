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
        assertThat(countOf(invitation, "TravelPlanMembershipChangedEvent.")).isEqualTo(2);
        assertThat(countOf(member, "TravelPlanMembershipChangedEvent.")).isEqualTo(3);

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

    // ── 방에서 빠지면 연결이 끊긴다 ──────────────────────────

    @Test
    void theBlockingIsDoneByTheServerNotTheScreen() throws IOException {
        String listener = service("TravelPlanMembershipChangedListener");

        /*
          구독은 SUBSCRIBE 한 번만 검사된다.
          끊지 않으면 내보내진 사람이 화면을 열어 둔 채
          그때부터의 채팅·일정·투표를 계속 받는다.
        */
        assertThat(listener)
                .contains("travelPlanRoomSessionRegistry.disconnect(travelPlanId, memberId)")
                // 붙잡고 있던 자리를 놓고
                .contains("releaseAllBySession(sessionId)")
                // 접속 표시에서도 뺀다
                .contains("travelPlanPresenceService.leave(sessionId)");

        String registry = service("TravelPlanRoomSessionRegistry");
        // 실제로 연결을 닫는다. 알림만 보내고 마는 것이 아니다
        assertThat(registry).contains("session.close(CloseStatus.NORMAL)");
    }

    @Test
    void theConnectionsToCutAreFoundByRoomAndMemberTogether() throws IOException {
        String registry = service("TravelPlanRoomSessionRegistry");

        // 방과 참여자로 좁혀야 다른 방을 보고 있는 연결이 함께 끊기지 않는다
        assertThat(registry)
                .contains("public List<String> disconnect(Long travelPlanId, Long memberId)")
                .contains("roomSessions.get(travelPlanId)")
                .contains("members.remove(memberId)");

        // 구독이 받아들여진 시점에 적는다. 접속 인사보다 먼저라 빠뜨리지 않는다
        String interceptor = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/"
                        + "TravelPlanWebSocketAuthInterceptor.java"),
                StandardCharsets.UTF_8);
        assertThat(between(interceptor,
                "if (command == StompCommand.SUBSCRIBE)", "if (command == StompCommand.SEND)"))
                .contains("travelPlanRoomSessionRegistry.watching(");
    }

    @Test
    void onlyLosingYourPlaceCutsTheLine() throws IOException {
        String member = service("TravelPlanMemberService");

        // 나가기와 내보내기는 그 사람을 지목한다
        assertThat(between(member, "public void leave(", "public void removeMember("))
                .contains("TravelPlanMembershipChangedEvent.revoked(travelPlanId, member.getId())");
        assertThat(between(member, "public void removeMember(", "public void allowRejoin("))
                .contains("TravelPlanMembershipChangedEvent.revoked(travelPlanId, target.getId())");

        // 방장 넘기기는 아무도 지목하지 않는다. 양쪽 다 여전히 ACTIVE 참여자다
        String transfer = between(member,
                "public void transferOwnership(", "private void requireActivePlan");
        assertThat(transfer)
                .contains("TravelPlanMembershipChangedEvent.changed(travelPlanId)")
                .doesNotContain("revoked(");

        // 참여와 재참여도 마찬가지다
        assertThat(service("TravelPlanInvitationService"))
                .contains("TravelPlanMembershipChangedEvent.changed(travelPlanId)")
                .doesNotContain("revoked(");
    }

    @Test
    void theScreenIsToldButIsNeverWhatDoesTheBlocking() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        assertThat(realtime)
                .contains("/user/queue/travel-plan-access")
                .contains("ACCESS_REVOKED")
                // 다시 붙기를 되풀이하지 않는다
                .contains("client.deactivate()")
                .contains("/travel-plans");

        // 알림을 놓쳐도 명단 조회가 막히는 것으로 알아챈다
        assertThat(realtime).contains("response.status === 403 || response.status === 404");

        // 개인 큐라 다른 사람 것을 볼 수 없다
        String interceptor = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/"
                        + "TravelPlanWebSocketAuthInterceptor.java"),
                StandardCharsets.UTF_8);
        assertThat(between(interceptor,
                "private boolean isOwnReplyQueue", "private Long sendableTravelPlanId"))
                .contains("TravelPlanMemberDestinations.ACCESS_QUEUE");
    }

    // ── 방장이 바뀌면 그 자리를 다시 받는다 ───────────────────

    @Test
    void whoIsOwnerIsAnsweredByTheServerNotGuessedOnScreen() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        /*
          멤버로 그려진 화면에는 방장 전용 markup 이 아예 없다.
          감추고 보이는 것만으로는 넘겨받은 사람 화면에 나타나게 할 수 없어
          그 자리를 서버에서 다시 받아 통째로 갈아 끼운다.
        */
        String refresh = between(realtime,
                "async function refreshOwnerActions()", "// ── 방에서 빠졌을 때");
        assertThat(refresh)
                .contains("/owner-actions/fragment")
                .contains("slot.innerHTML = html")
                // 늦게 도착한 예전 응답이 최신 상태를 덮지 않는다
                .contains("if (ownerActionsRequest !== sequence) return")
                // 갈아 끼운 markup 에는 동작이 없다. 붙이는 쪽에 알린다
                .contains("travelplan:owner-actions-updated");

        // 화면이 역할을 스스로 정하지 않는다
        for (String guess : new String[]{"=== \"OWNER\"", "role === ", "isOwner ="}) {
            assertThat(realtime).as("화면이 역할을 짐작함: %s", guess).doesNotContain(guess);
        }
    }

    @Test
    void theSameNoticeBringsBothTheListAndTheOwnerActions() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        // 명단 변경 알림 하나로 둘 다 다시 읽는다. 새 알림을 만들지 않는다
        String subscription = between(realtime,
                "client.subscribe(`/topic/travel-plans/${planId}/members`", "});");
        assertThat(subscription)
                .contains("refreshMembers()")
                .contains("refreshOwnerActions()");

        // 끊겼다 다시 붙었을 때도 그사이 바뀐 방장을 따라잡는다
        assertThat(between(realtime, "if (connectedBefore) {", "connectedBefore = true;"))
                .contains("refreshOwnerActions()");
    }

    @Test
    void theSwappedMarkupGetsItsBehaviourBackOnBothSides() throws IOException {
        /*
          갈아 끼운 요소는 전부 새것이라 예전에 붙여 둔 동작이 없다.
          다시 붙이지 않으면 넘겨받은 사람 화면에서 버튼이 눌리지 않는다.
        */
        String finalizeJs = resource("/static/js/travel-plan-finalize.js");
        assertThat(finalizeJs)
                .contains("function bindOwnerControls()")
                .contains("document.addEventListener(\"travelplan:owner-actions-updated\"");
        // 한 번 담아 두지 않고 갈릴 때마다 다시 찾는다
        assertThat(finalizeJs)
                .doesNotContain("const root = document.querySelector(\"[data-travel-plan-finalize]")
                .doesNotContain("const modal = document.querySelector(");

        String scheduler = resource("/static/js/travel-plan-scheduler.js");
        assertThat(scheduler)
                .contains("function bindInvite()")
                .contains("document.addEventListener(\"travelplan:owner-actions-updated\"");
        // 화면에서 사라진 예전 popover 를 들고 있지 않는다
        assertThat(between(scheduler, "function registerPopover(", "\n    }"))
                .contains("isConnected");
    }

    @Test
    void aPollBeingLookedAtLearnsTheNewOwnersRights() throws IOException {
        String poll = resource("/static/js/travel-plan-poll.js");

        /*
          마감·삭제를 누가 할 수 있는지가 방장과 함께 바뀐다.
          보고 있던 상세를 서버에서 다시 읽어 그 답을 새로 받는다.
        */
        assertThat(poll)
                .contains("document.addEventListener(\"travelplan:owner-actions-updated\"")
                .contains("refreshDetail()");
        // 버튼을 넣고 뺄지는 서버가 준 값만 본다
        assertThat(poll).contains("if (poll.closable)").contains("if (poll.deletable)");
    }

    @Test
    void theOwnerActionsAreReadFromTheServerWithTheSameGuardAsTheRest() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/com/example/travlediary/controller/travelplan/"
                        + "TravelPlanController.java"),
                StandardCharsets.UTF_8);
        String endpoint = between(controller,
                "public String ownerActionsFragment(", "// A 일정 수정");

        // 접근 권한은 상세와 같은 길로 확인한다(비참여자는 404)
        assertThat(endpoint)
                .contains("travelPlanService.getActivePlanMembers(userId, travelPlanId)")
                .contains("addInviteState(model, userId, travelPlanId)");
        // 사람 하나 바뀌었다고 방의 일정 전체를 다시 읽지 않는다
        assertThat(endpoint).doesNotContain("getActivePlanDetail");

        // 처음 그릴 때와 실시간 갱신이 같은 조각을 쓴다
        assertThat(controller).contains("travelplan/fragments/owner-actions :: ownerActions(");
        assertThat(resource("/templates/travelplan/detail.html"))
                .contains("travelplan/fragments/owner-actions :: ownerActions(");
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
