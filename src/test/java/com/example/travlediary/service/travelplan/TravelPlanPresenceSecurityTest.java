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
    private SimpMessagingTemplate simpMessagingTemplate;

    private TravelPlanRoomAccess roomAccess;
    private TravelPlanPresenceService presence;
    private TravelPlanWebSocketAuthInterceptor interceptor;
    private TravelPlanPresenceController controller;

    @BeforeEach
    void setUp() {
        roomAccess = new TravelPlanRoomAccess(travelPlanMapper, travelPlanItemMapper);
        presence = new TravelPlanPresenceService();
        interceptor = new TravelPlanWebSocketAuthInterceptor(roomAccess);
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
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
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
