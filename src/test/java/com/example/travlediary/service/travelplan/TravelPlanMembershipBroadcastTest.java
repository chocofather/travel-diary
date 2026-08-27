package com.example.travlediary.service.travelplan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 명단이 바뀐 뒤의 뒷정리.
 *
 * <p>알림에는 사람 수도 이름도 싣지 않는다.
 * 받은 화면이 서버에서 최신 명단을 다시 읽게 해,
 * 같은 알림을 두 번 받아도 숫자가 두 번 늘지 않게 하기 위해서다.
 *
 * <p>그리고 방에서 빠진 사람의 연결은 여기서 끊는다.
 * 구독은 SUBSCRIBE 한 번만 검사되므로, 끊지 않으면 화면을 열어 둔 채로
 * 그때부터의 채팅·일정·투표를 계속 받는다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanMembershipBroadcastTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long MEMBER_B = 12L;
    private static final Long MEMBER_C = 13L;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private TravelPlanRoomSessionRegistry sessions;
    private TravelPlanEditorRealtimeService editor;
    private TravelPlanPresenceService presence;
    private TravelPlanMembershipChangedListener listener;

    @BeforeEach
    void setUp() {
        sessions = new TravelPlanRoomSessionRegistry();
        editor = new TravelPlanEditorRealtimeService();
        presence = new TravelPlanPresenceService();
        listener = new TravelPlanMembershipChangedListener(
                simpMessagingTemplate, sessions, editor, presence);
    }

    // ── 명단이 바뀌었다는 알림 ───────────────────────────────

    @Test
    void theNoticeGoesToThatRoomAndNoOther() {
        listener.onMembershipChanged(TravelPlanMembershipChangedEvent.changed(PLAN_ID));

        verify(simpMessagingTemplate).convertAndSend(
                "/topic/travel-plans/42/members",
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", PLAN_ID));
    }

    @Test
    void theNoticeIsSeparateFromWhoIsOnlineRightNow() {
        /*
          "참여자 N/8" 과 "접속 중 N명" 은 서로 다른 값이다.
          같은 topic 을 쓰면 한쪽 숫자가 다른 쪽 자리에 들어간다.
        */
        assertThat(TravelPlanMemberDestinations.topic(PLAN_ID))
                .isNotEqualTo(TravelPlanPresenceDestinations.topic(PLAN_ID));
        assertThat(TravelPlanMemberDestinations.travelPlanIdOf(
                TravelPlanPresenceDestinations.topic(PLAN_ID))).isNull();
    }

    @Test
    void theNoticeCarriesNoHeadcountOfItsOwn() {
        listener.onMembershipChanged(TravelPlanMembershipChangedEvent.changed(PLAN_ID));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate).convertAndSend(anyString(), captor.capture());
        Map<String, Object> payload = (Map<String, Object>) captor.getValue();
        // 방 번호와 종류뿐이다. 누가 빠졌는지도 방에는 알리지 않는다
        assertThat(payload).containsOnlyKeys("type", "travelPlanId");
    }

    @Test
    void thesameNoticeTwiceStillJustMeansReadItAgain() {
        listener.onMembershipChanged(TravelPlanMembershipChangedEvent.changed(PLAN_ID));
        listener.onMembershipChanged(TravelPlanMembershipChangedEvent.changed(PLAN_ID));

        verify(simpMessagingTemplate, times(2)).convertAndSend(
                "/topic/travel-plans/42/members",
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", PLAN_ID));
    }

    @Test
    void nothingIsAnnouncedUntilTheChangeIsActuallySaved() throws NoSuchMethodException {
        // 되돌아간 변경 때문에 멀쩡한 사람의 연결이 끊기면 안 된다
        Method method = TravelPlanMembershipChangedListener.class.getDeclaredMethod(
                "onMembershipChanged", TravelPlanMembershipChangedEvent.class);

        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    // ── 방에서 빠진 사람의 연결을 끊는다 ─────────────────────

    @Test
    void whoeverWasPutOutHasTheirConnectionClosed() throws Exception {
        WebSocketSession session = givenWatching("ws-b", PLAN_ID, MEMBER_B);

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        // 구독은 한 번만 검사되므로 연결 자체를 끊어야 이후 내용이 가지 않는다
        verify(session).close(CloseStatus.NORMAL);
        assertThat(sessions.isWatching(PLAN_ID, MEMBER_B, "ws-b")).isFalse();
    }

    @Test
    void everyTabTheyLeftOpenIsClosed() throws Exception {
        WebSocketSession first = givenWatching("ws-b1", PLAN_ID, MEMBER_B);
        WebSocketSession second = givenWatching("ws-b2", PLAN_ID, MEMBER_B);

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        verify(first).close(CloseStatus.NORMAL);
        verify(second).close(CloseStatus.NORMAL);
    }

    @Test
    void everyoneElseKeepsWatching() throws Exception {
        WebSocketSession leaving = givenWatching("ws-b", PLAN_ID, MEMBER_B);
        WebSocketSession staying = givenWatching("ws-c", PLAN_ID, MEMBER_C);
        // 같은 사람이 다른 방을 보고 있는 탭도 끊기면 안 된다
        WebSocketSession elsewhere = givenWatching("ws-b-other", OTHER_PLAN_ID, MEMBER_B);

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        verify(leaving).close(CloseStatus.NORMAL);
        verify(staying, never()).close(any());
        verify(elsewhere, never()).close(any());
        assertThat(sessions.isWatching(PLAN_ID, MEMBER_C, "ws-c")).isTrue();
        assertThat(sessions.isWatching(OTHER_PLAN_ID, MEMBER_B, "ws-b-other")).isTrue();
    }

    @Test
    void theyAreTakenOffTheOnlineCountTheSameMoment() throws Exception {
        givenWatching("ws-b", PLAN_ID, MEMBER_B);
        givenWatching("ws-c", PLAN_ID, MEMBER_C);
        presence.join(PLAN_ID, MEMBER_B, "ws-b");
        presence.join(PLAN_ID, MEMBER_C, "ws-c");

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        // "접속 중 N명" 이 곧바로 줄어든다. 끊긴 알림을 기다리지 않는다
        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_C);
        verify(simpMessagingTemplate).convertAndSend(
                eq("/topic/travel-plans/42/presence"), any(Object.class));
    }

    @Test
    void theSpotTheyWereWritingInIsLetGo() throws Exception {
        givenWatching("ws-b", PLAN_ID, MEMBER_B);
        editor.tryAcquire(new TravelPlanEditorRealtimeService.EditorLock(
                PLAN_ID, "ITEM:500", "ws-b", "EDIT", 100L, 500L, null,
                MEMBER_B, "쭈니", "", ""));

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        // 놓지 않으면 남은 사람 화면에 "편집 중" 표시가 영원히 남는다
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
        verify(simpMessagingTemplate).convertAndSend(
                eq("/topic/travel-plans/42/editor"), any(Object.class));
    }

    @Test
    void theyAreToldBeforeTheLineGoesDead() {
        givenWatching("ws-b", PLAN_ID, MEMBER_B);

        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        // 화면이 멈춘 채로 남지 않게 한 줄 보낸다. 막는 것은 어디까지나 연결을 끊는 쪽이다
        verify(simpMessagingTemplate).convertAndSendToUser(
                eq("ws-b"), eq("/queue/travel-plan-access"), any(Object.class), any(Map.class));
    }

    @Test
    void handingTheRoomOverCutsNobodyOff() throws Exception {
        WebSocketSession owner = givenWatching("ws-a", PLAN_ID, MEMBER_C);
        WebSocketSession member = givenWatching("ws-b", PLAN_ID, MEMBER_B);

        // 역할만 바뀐다. 양쪽 다 여전히 ACTIVE 참여자다
        listener.onMembershipChanged(TravelPlanMembershipChangedEvent.changed(PLAN_ID));

        verify(owner, never()).close(any());
        verify(member, never()).close(any());
        verify(simpMessagingTemplate, never()).convertAndSendToUser(
                anyString(), anyString(), any(Object.class), any(Map.class));
    }

    @Test
    void someoneWhoHadNoTabOpenIsNoProblem() {
        // 화면을 열어 두지 않았던 사람도 같은 길을 지나간다
        listener.onMembershipChanged(
                TravelPlanMembershipChangedEvent.revoked(PLAN_ID, MEMBER_B));

        verify(simpMessagingTemplate).convertAndSend(
                "/topic/travel-plans/42/members",
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", PLAN_ID));
    }

    /** 그 연결이 그 방을 보고 있는 상태. 구독이 받아들여진 뒤와 같다. */
    private WebSocketSession givenWatching(String sessionId, Long travelPlanId, Long memberId) {
        WebSocketSession session = mock(WebSocketSession.class);
        // 끊기지 않는 연결에서는 이 둘을 묻지 않는다
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.isOpen()).thenReturn(true);
        sessions.register(session);
        sessions.watching(travelPlanId, memberId, sessionId);
        return session;
    }
}
