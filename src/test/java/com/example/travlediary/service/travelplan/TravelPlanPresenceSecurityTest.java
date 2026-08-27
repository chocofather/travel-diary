package com.example.travlediary.service.travelplan;

import com.example.travlediary.config.TravelPlanWebSocketAuthInterceptor;
import com.example.travlediary.controller.travelplan.TravelPlanPresenceController;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실시간 연결의 보안 경계.
 * 목적지에 적힌 방 번호를 믿지 않고 매번 ACTIVE 참여자인지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelPlanPresenceSecurityTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long USER_ID = 7L;
    private static final Long MEMBER_ID = 11L;
    private static final String TOPIC = "/topic/travel-plans/42/presence";

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private com.example.travlediary.repository.travelplan.TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private com.example.travlediary.repository.travelplan.TravelPlanAlternativeMapper
            travelPlanAlternativeMapper;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private TravelPlanRoomAccess roomAccess;
    private TravelPlanPresenceService presence;
    private TravelPlanRoomSessionRegistry sessions;
    private TravelPlanWebSocketAuthInterceptor interceptor;
    private TravelPlanPresenceController controller;

    @BeforeEach
    void setUp() {
        roomAccess = new TravelPlanRoomAccess(
                travelPlanMapper, travelPlanItemMapper, travelPlanAlternativeMapper);
        presence = new TravelPlanPresenceService();
        sessions = new TravelPlanRoomSessionRegistry();
        interceptor = new TravelPlanWebSocketAuthInterceptor(roomAccess, sessions);
        controller = new TravelPlanPresenceController(roomAccess, presence, simpMessagingTemplate);
    }

    // ── 구독 ────────────────────────────────────────────────

    @Test
    void anActiveMemberMaySubscribeToTheirOwnRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(subscribe(TOPIC, principal()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void anActiveOwnerMaySubscribeToo() {
        givenActivePlan();
        givenMembership(TravelPlanRole.OWNER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(subscribe(TOPIC, principal()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void whoeverLeftOrWasRemovedCannotSubscribe() {
        givenActivePlan();
        // ACTIVE 조건이 걸린 조회라 LEFT / REMOVED 는 여기서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(subscribe(TOPIC, principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void someoneWhoNeverJoinedCannotSubscribe() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(subscribe(TOPIC, principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnauthenticatedConnectionIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe(TOPIC, null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(AccessDeniedException.class);

        // 로그인 정보가 없으면 방을 조회하지도 않는다
        verify(travelPlanMapper, never()).findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
    }

    @Test
    void aRoomThatIsNoLongerActiveCannotBeWatched() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(subscribe(TOPIC, principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribingToAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        // 다른 방에는 참여 기록이 없다
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/43/presence", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anActiveMemberMayAlsoWatchTheirOwnRoomsSchedule() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/schedule", principal()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void theScheduleTopicIsCheckedJustLikePresence() {
        givenActivePlan();
        // LEFT / REMOVED / 비참여자는 ACTIVE 조회에서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/schedule", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        // 비로그인도 마찬가지다
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/schedule", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aScheduleTopicOfAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/43/schedule", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 구독한 연결을 적어 둔다 ──────────────────────────────

    @Test
    void anAcceptedSubscriptionIsRememberedSoItCanBeCutLater() {
        /*
          구독은 여기서 한 번만 검사된다.
          나중에 방에서 빠졌을 때 끊을 연결을 알아야 하므로 지금 적어 둔다.
          접속 인사(presence/join)보다 구독이 먼저라, 그 사이에 자격을 잃어도 빠뜨리지 않는다.
        */
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        interceptor.preSend(subscribe(TOPIC, principal(), "ws-1"), null);

        assertThat(sessions.isWatching(PLAN_ID, MEMBER_ID, "ws-1")).isTrue();
    }

    @Test
    void arefusedSubscriptionIsNotRemembered() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(TOPIC, principal(), "ws-1"), null))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(sessions.isWatching(PLAN_ID, MEMBER_ID, "ws-1")).isFalse();
    }

    @Test
    void theConnectionIsFiledUnderTheRoomItActuallySubscribedTo() {
        // 방 안에서의 참여 id 로 적는다. 다른 방의 같은 사람과 섞이지 않는다
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        interceptor.preSend(subscribe(TOPIC, principal(), "ws-1"), null);

        assertThat(sessions.isWatching(OTHER_PLAN_ID, MEMBER_ID, "ws-1")).isFalse();
        assertThat(sessions.sessionsOf(PLAN_ID, MEMBER_ID)).containsExactly("ws-1");
    }

    // ── 참여자 명단 ─────────────────────────────────────────

    @Test
    void anActiveMemberMayWatchTheirOwnRoomsMemberList() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/members", principal()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void theMemberListTopicIsCheckedJustLikePresence() {
        givenActivePlan();
        // LEFT / REMOVED / 비참여자는 ACTIVE 조회에서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/members", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        // 비로그인도 마찬가지다
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/members", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aMemberListOfAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/43/members", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noOneCanPushAMemberListOfTheirOwn() {
        // 명단 알림은 서버만 보낸다. 보내는 목적지가 아니다
        givenActivePlan();
        givenMembership(TravelPlanRole.OWNER, TravelPlanMemberStatus.ACTIVE);

        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/members", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 채팅 ────────────────────────────────────────────────

    @Test
    void anActiveMemberMayWatchAndUseTheirOwnRoomsChat() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/chat", principal()), null))
                .doesNotThrowAnyException();
        for (String action : new String[]{"send", "delete", "read"}) {
            assertThatCode(() -> interceptor.preSend(
                    send("/app/travel-plans/42/chat/" + action, principal()), null))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void whoeverLeftOrWasRemovedCannotWatchOrUseTheChat() {
        givenActivePlan();
        // ACTIVE 조건이 걸린 조회라 LEFT / REMOVED / 비참여자는 여기서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/chat", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        // 이미 붙어 있던 연결이라도 보낼 때마다 다시 확인한다
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/chat/send", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/chat/delete", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/chat/read", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aChatOfAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/43/chat", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/43/chat/send", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRoomThatEndedIsClosedForChatting() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/chat", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/chat/send", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnknownChatDestinationIsRefused() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        // 목적지에 방 번호가 적혀 있어도 정해 둔 것이 아니면 받지 않는다
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/chat/purge", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/chat/all", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theChatReplyQueueIsMineAlone() {
        // 나에게만 오는 개인 큐라 방 검사를 따로 하지 않는다
        assertThatCode(() -> interceptor.preSend(
                subscribe("/user/queue/travel-plan-chat", principal()), null))
                .doesNotThrowAnyException();
        // 로그인은 여전히 필요하다
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/user/queue/travel-plan-chat", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 투표 ────────────────────────────────────────────────

    @Test
    void anActiveMemberMayWatchTheirOwnRoomsPolls() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/polls", principal()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void whoeverLeftOrWasRemovedCannotWatchThePolls() {
        givenActivePlan();
        // ACTIVE 조건이 걸린 조회라 LEFT / REMOVED / 비참여자는 여기서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/polls", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        // 비로그인도 마찬가지다
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/polls", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aPollTopicOfAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/43/polls", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRoomThatEndedCannotHaveItsPollsWatched() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/polls", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nobodySendsToThePollTopic() {
        // 투표 만들기는 기존 HTTP 경로다. 보내는 목적지를 두지 않는다
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/polls", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/polls/create", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRoomThatEndedCannotHaveItsScheduleWatched() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/schedule", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 작성 중 상태 채널 ───────────────────────────────────

    @Test
    void anActiveMemberMayWatchAndSendOnTheEditorChannel() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        assertThatCode(() -> interceptor.preSend(
                subscribe("/topic/travel-plans/42/editor", principal()), null))
                .doesNotThrowAnyException();
        for (String destination : new String[]{
                "/app/travel-plans/42/editor/lock",
                "/app/travel-plans/42/editor/draft",
                "/app/travel-plans/42/editor/unlock",
                "/app/travel-plans/42/editor/sync",
                "/app/travel-plans/42/presence/join"}) {
            assertThatCode(() -> interceptor.preSend(send(destination, principal()), null))
                    .as("destination=%s", destination)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void whoeverIsNotAnActiveMemberCannotSendOnTheEditorChannel() {
        givenActivePlan();
        // LEFT / REMOVED / 비참여자는 ACTIVE 조회에서 비어 온다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        for (String destination : new String[]{
                "/app/travel-plans/42/editor/lock",
                "/app/travel-plans/42/editor/draft",
                "/app/travel-plans/42/editor/unlock"}) {
            assertThatThrownBy(() -> interceptor.preSend(send(destination, principal()), null))
                    .as("destination=%s", destination)
                    .isInstanceOf(AccessDeniedException.class);
        }
        // 비로그인도 마찬가지다
        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/editor/lock", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anEditorSendForAnotherRoomIsCheckedAgainstThatRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(OTHER_PLAN_ID));
        when(travelPlanMapper.findMemberByPlanAndUser(OTHER_PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/43/editor/lock", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRoomThatEndedAcceptsNoEditorTraffic() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(
                send("/app/travel-plans/42/editor/lock", principal()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nothingButTheKnownSendDestinationsIsAccepted() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        for (String destination : new String[]{
                "/app/travel-plans/42/editor",
                "/app/travel-plans/42/editor/save",
                "/app/travel-plans/42/items",
                "/app/travel-plans/abc/editor/lock",
                "/app/anything",
                null}) {
            assertThatThrownBy(() -> interceptor.preSend(send(destination, principal()), null))
                    .as("destination=%s", destination)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void nothingButTheRoomPresenceTopicCanBeSubscribed() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        for (String destination : new String[]{
                "/topic",
                "/topic/travel-plans",
                "/topic/travel-plans/42",
                "/topic/travel-plans/42/presence/extra",
                "/topic/travel-plans/abc/presence",
                "/topic/travel-plans/42/schedule/extra",
                "/topic/travel-plans/abc/schedule",
                "/topic/travel-plans/**",
                "/topic/**",
                null}) {
            assertThatThrownBy(() -> interceptor.preSend(subscribe(destination, principal()), null))
                    .as("destination=%s", destination)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── 들어옴 ──────────────────────────────────────────────

    @Test
    void joiningRegistersTheConnectionAndTellsTheRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        controller.join(PLAN_ID, principal(), headers("session-1"));

        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_ID);
        verify(simpMessagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(TOPIC), any(Object.class));
    }

    @Test
    void joiningUsesTheServersMemberIdNotAnythingTheClientSent() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);

        controller.join(PLAN_ID, principal(), headers("session-1"));

        // 클라이언트는 memberId 를 보내지 않는다. 서버가 조회한 값만 쓴다
        assertThat(presence.onlineMemberIds(PLAN_ID)).containsExactly(MEMBER_ID);
        verify(travelPlanMapper).findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE");
    }

    @Test
    void whoeverIsNotAnActiveMemberCannotJoin() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> controller.join(PLAN_ID, principal(), headers("session-1")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void anUnauthenticatedJoinIsRefused() {
        assertThatThrownBy(() -> controller.join(PLAN_ID, null, headers("session-1")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
    }

    @Test
    void joiningARoomThatEndedIsRefused() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> controller.join(PLAN_ID, principal(), headers("session-1")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 나감 ────────────────────────────────────────────────

    @Test
    void aDisconnectRemovesExactlyThatConnectionAndTellsTheRoom() {
        givenActivePlan();
        givenMembership(TravelPlanRole.MEMBER, TravelPlanMemberStatus.ACTIVE);
        controller.join(PLAN_ID, principal(), headers("session-1"));

        controller.onDisconnect(new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                this, disconnectMessage("session-1"), "session-1", null));

        assertThat(presence.onlineMemberIds(PLAN_ID)).isEmpty();
        verify(simpMessagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(org.mockito.ArgumentMatchers.eq(TOPIC), any(Object.class));
    }

    @Test
    void anUnknownDisconnectIsQuietlyIgnored() {
        assertThatCode(() -> controller.onDisconnect(
                new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                        this, disconnectMessage("never-seen"), "never-seen", null)))
                .doesNotThrowAnyException();

        // 알릴 방이 없으므로 브로드캐스트도 없다
        verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private Message<byte[]> subscribe(String destination, Principal principal) {
        return subscribe(destination, principal, null);
    }

    private Message<byte[]> subscribe(String destination, Principal principal, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        if (sessionId != null) accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> send(String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connect(Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> disconnectMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private SimpMessageHeaderAccessor headers(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        return accessor;
    }

    private Principal principal() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("minjun");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
    }

    private void givenActivePlan() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan(PLAN_ID));
    }

    private TravelPlan activePlan(Long travelPlanId) {
        TravelPlan plan = new TravelPlan();
        plan.setId(travelPlanId);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        return plan;
    }

    private void givenMembership(TravelPlanRole role, TravelPlanMemberStatus status) {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(MEMBER_ID);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setDisplayName("민준");
        member.setRole(role);
        member.setStatus(status);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(member);
    }
}
