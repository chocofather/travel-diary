package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanChatEventDto;
import com.example.travlediary.dto.TravelPlanChatMessageDto;
import com.example.travlediary.dto.TravelPlanChatTimelineDto;
import com.example.travlediary.dto.TravelPlanChatTimelineItemDto;
import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanChatMessage;
import com.example.travlediary.model.TravelPlanChatMessageType;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.travelplan.TravelPlanAlternativeMapper;
import com.example.travlediary.repository.travelplan.TravelPlanChatMapper;
import com.example.travlediary.repository.travelplan.TravelPlanItemMapper;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 방 채팅.
 *
 * <p>보낼 수 있는 사람은 살아 있는 방의 ACTIVE 참여자뿐이고,
 * 보낸 사람이 누구인지는 언제나 서버가 정한다.
 * 저장된 뒤에만 알림이 나가고, 지운 메시지는 행이 남는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelPlanChatServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final Long MEMBER_ID = 11L;
    private static final Long OTHER_MEMBER_ID = 12L;
    private static final Long MESSAGE_ID = 500L;

    @Mock
    private TravelPlanChatMapper travelPlanChatMapper;
    @Mock
    private com.example.travlediary.repository.travelplan.TravelPlanPollMapper travelPlanPollMapper;
    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private TravelPlanAlternativeMapper travelPlanAlternativeMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TravelPlanChatService chatService;

    @BeforeEach
    void setUp() {
        TravelPlanRoomAccess roomAccess = new TravelPlanRoomAccess(
                travelPlanMapper, travelPlanItemMapper, travelPlanAlternativeMapper);
        chatService = new TravelPlanChatService(
                travelPlanChatMapper, travelPlanPollMapper, roomAccess, eventPublisher);
    }

    // ── 보내기 ──────────────────────────────────────────────

    @Test
    void anActiveOwnerCanSend() {
        givenRoom(TravelPlanRole.OWNER);
        givenInsertSucceeds();

        chatService.sendMessage(principal(), PLAN_ID, "숙소 어디가 좋을까?");

        assertThat(captureInsert().getContent()).isEqualTo("숙소 어디가 좋을까?");
    }

    @Test
    void anActiveMemberCanSendToo() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        chatService.sendMessage(principal(), PLAN_ID, "난 서귀포가 좋아");

        assertThat(captureInsert().getContent()).isEqualTo("난 서귀포가 좋아");
    }

    @Test
    void whoeverLeftCannotSend() {
        // ACTIVE 조건이 걸린 조회라 LEFT 는 여기서 비어 온다
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, "안녕"))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanChatMapper, never()).insertMessage(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void whoeverWasRemovedCannotSend() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, "안녕"))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanChatMapper, never()).insertMessage(any());
    }

    @Test
    void someoneWhoNeverJoinedCannotSend() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, "안녕"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aFinishedRoomIsClosedForChatting() {
        // 끝난 방은 ACTIVE 조건에서 걸린다
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, "안녕"))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanChatMapper, never()).insertMessage(any());
    }

    @Test
    void aBlankMessageIsNotAMessage() {
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, "   \n  "))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanChatMapper, never()).insertMessage(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aTooLongMessageIsRefused() {
        givenRoom(TravelPlanRole.MEMBER);
        String tooLong = "가".repeat(TravelPlanChatService.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> chatService.sendMessage(principal(), PLAN_ID, tooLong))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanChatMapper, never()).insertMessage(any());
    }

    @Test
    void lineBreaksInsideTheMessageAreKept() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        chatService.sendMessage(principal(), PLAN_ID, "  첫째 줄\n둘째 줄\n\n넷째 줄  ");

        // 앞뒤 공백만 덜어 내고 안쪽 줄바꿈은 그대로 둔다
        assertThat(captureInsert().getContent()).isEqualTo("첫째 줄\n둘째 줄\n\n넷째 줄");
    }

    @Test
    void otherLanguagesAndEmojiSurviveUnchanged() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        String mixed = "제주 🏝 いいね! Let's go 🚗";
        chatService.sendMessage(principal(), PLAN_ID, mixed);

        assertThat(captureInsert().getContent()).isEqualTo(mixed);
    }

    @Test
    void htmlIsStoredAsPlainTextNotStripped() {
        // 저장 정책은 plain text 다. 화면이 글자로만 그리므로 여기서 지우지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        chatService.sendMessage(principal(), PLAN_ID, "<script>alert(1)</script>");

        assertThat(captureInsert().getContent()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void theSenderIsDecidedByTheServerNotTheClient() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        TravelPlanChatMessageDto sent =
                chatService.sendMessage(principal(), PLAN_ID, "숙소 어디가 좋을까?");

        TravelPlanChatMessage saved = captureInsert();
        assertThat(saved.getSenderMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getTravelPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getMessageType()).isEqualTo(TravelPlanChatMessageType.USER);
        // 이름도 방 안의 표시 이름에서만 나온다
        assertThat(sent.displayName()).isEqualTo("민준");
        assertThat(sent.memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void whatGoesOutCarriesNoAccountInformation() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        TravelPlanChatMessageDto sent = chatService.sendMessage(principal(), PLAN_ID, "안녕");

        assertThat(sent.toString())
                .contains("민준")
                .doesNotContain("minjun")
                .doesNotContain("@")
                .doesNotContain("userId");
    }

    @Test
    void theRoomHearsAboutItOnlyAfterItIsSaved() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertSucceeds();

        chatService.sendMessage(principal(), PLAN_ID, "숙소 어디가 좋을까?");

        // Service 는 알림을 직접 보내지 않는다. 커밋 뒤에 나가도록 이벤트만 발행한다
        TravelPlanChatChangedEvent event = captureEvent();
        assertThat(event.travelPlanId()).isEqualTo(PLAN_ID);
        assertThat(event.payload().type()).isEqualTo(TravelPlanChatEventDto.MESSAGE_CREATED);
        assertThat(event.payload().message().content()).isEqualTo("숙소 어디가 좋을까?");
    }

    @Test
    void theServiceNeverTalksToWebSocketItself() {
        // Service 가 SimpMessagingTemplate 을 들고 있으면 롤백된 메시지가 먼저 나갈 수 있다
        assertThat(TravelPlanChatService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(
                        org.springframework.messaging.simp.SimpMessagingTemplate.class);
    }

    // ── 지우기 ──────────────────────────────────────────────

    @Test
    void theWriterCanDeleteTheirOwnMessage() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.markMessageDeleted(MESSAGE_ID, PLAN_ID, MEMBER_ID)).thenReturn(1);

        chatService.deleteMessage(principal(), PLAN_ID, MESSAGE_ID);

        TravelPlanChatChangedEvent event = captureEvent();
        assertThat(event.payload().type()).isEqualTo(TravelPlanChatEventDto.MESSAGE_DELETED);
        assertThat(event.payload().messageId()).isEqualTo(MESSAGE_ID);
        // 지운 메시지도 행은 남는다. 지우는 SQL 은 부르지 않는다
        assertThat(TravelPlanChatMapper.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void anotherMemberCannotDeleteSomeoneElsesMessage() {
        givenRoom(TravelPlanRole.MEMBER);
        // 보낸 사람 조건이 SQL 안에 있어 한 건도 바뀌지 않는다
        when(travelPlanChatMapper.markMessageDeleted(MESSAGE_ID, PLAN_ID, MEMBER_ID)).thenReturn(0);
        when(travelPlanChatMapper.findByIdAndPlanId(MESSAGE_ID, PLAN_ID))
                .thenReturn(messageOf(OTHER_MEMBER_ID, null));

        assertThatThrownBy(() -> chatService.deleteMessage(principal(), PLAN_ID, MESSAGE_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void beingTheOwnerDoesNotAllowDeletingOthersMessages() {
        givenRoom(TravelPlanRole.OWNER);
        when(travelPlanChatMapper.markMessageDeleted(MESSAGE_ID, PLAN_ID, MEMBER_ID)).thenReturn(0);
        when(travelPlanChatMapper.findByIdAndPlanId(MESSAGE_ID, PLAN_ID))
                .thenReturn(messageOf(OTHER_MEMBER_ID, null));

        assertThatThrownBy(() -> chatService.deleteMessage(principal(), PLAN_ID, MESSAGE_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void deletingTwiceIsQuietlyAccepted() {
        // 이미 지워져 있으면 화면도 이미 지움으로 보이고 있다. 두 번째 알림은 내보내지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.markMessageDeleted(MESSAGE_ID, PLAN_ID, MEMBER_ID)).thenReturn(0);
        when(travelPlanChatMapper.findByIdAndPlanId(MESSAGE_ID, PLAN_ID))
                .thenReturn(messageOf(MEMBER_ID, Timestamp.valueOf("2026-08-24 17:10:00")));

        chatService.deleteMessage(principal(), PLAN_ID, MESSAGE_ID);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDeletedMessageKeepsItsPlaceButLosesItsWords() {
        TravelPlanChatMessage deleted =
                messageOf(MEMBER_ID, Timestamp.valueOf("2026-08-24 17:10:00"));
        deleted.setContent("원래 내용");

        TravelPlanChatMessageDto dto = TravelPlanChatMessageDto.of(deleted);

        assertThat(dto.deleted()).isTrue();
        // 원문이 화면까지 나가면 가려도 소용이 없다
        assertThat(dto.content()).isNull();
        assertThat(dto.id()).isEqualTo(MESSAGE_ID);
    }

    @Test
    void whoeverLeftCannotDelete() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.deleteMessage(principal(), PLAN_ID, MESSAGE_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanChatMapper, never()).markMessageDeleted(anyLong(), anyLong(), anyLong());
    }

    // ── 기록(대화 + 투표 알림) ──────────────────────────────

    @Test
    void theOldestThingComesFirstOnScreen() {
        givenRoom(TravelPlanRole.MEMBER);
        // DB 는 최신순으로 읽는다
        when(travelPlanChatMapper.findRecentMessages(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(messageAt(3L, "셋째", 30), messageAt(2L, "둘째", 20),
                        messageAt(1L, "첫째", 10)));

        assertThat(chatService.timeline(principal(), PLAN_ID, null, null).items())
                .extracting(TravelPlanChatTimelineItemDto::content)
                .containsExactly("첫째", "둘째", "셋째");
    }

    @Test
    void aCreatedPollSitsInTheConversationWhereItHappened() {
        // 대화 사이에 있었던 일이라 그 시각 자리에 놓인다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findRecentMessages(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(messageAt(2L, "나중 얘기", 30), messageAt(1L, "먼저 얘기", 10)));
        when(travelPlanPollMapper.findRecentPolls(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(pollAt(900L, "숙소 위치는?", 20)));

        List<TravelPlanChatTimelineItemDto> items =
                chatService.timeline(principal(), PLAN_ID, null, null).items();

        assertThat(items)
                .extracting(TravelPlanChatTimelineItemDto::type)
                .containsExactly("MESSAGE", "POLL_CREATED", "MESSAGE");
        assertThat(items.get(1).pollId()).isEqualTo(900L);
        assertThat(items.get(1).pollTitle()).isEqualTo("숙소 위치는?");
        assertThat(items.get(1).creatorDisplayName()).isEqualTo("민준");
        // 투표의 선택지까지 채팅에 옮겨 적지 않는다
        assertThat(items.get(1).content()).isNull();
    }

    @Test
    void bothKindsAreCutByTheirOwnIdSoNeitherIsEverLost() {
        // 대화 번호만 보고 자르면 그 사이의 투표 알림이 영영 빠진다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findMessagesBefore(
                PLAN_ID, 10L, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(messageAt(9L, "아홉", 90), messageAt(8L, "여덟", 80)));
        when(travelPlanPollMapper.findPollsBefore(
                PLAN_ID, 5L, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(pollAt(4L, "지난 투표", 85)));

        TravelPlanChatTimelineDto page = chatService.timeline(principal(), PLAN_ID, 10L, 5L);

        assertThat(page.items())
                .extracting(TravelPlanChatTimelineItemDto::type)
                .containsExactly("MESSAGE", "POLL_CREATED", "MESSAGE");
        // 다음 페이지 기준도 각자 돌려준다
        assertThat(page.nextBeforeMessageId()).isEqualTo(8L);
        assertThat(page.nextBeforePollId()).isEqualTo(4L);
    }

    @Test
    void aSideThatRanOutIsNotAskedAgain() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findMessagesBefore(
                PLAN_ID, 10L, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(messageAt(9L, "아홉", 90)));
        // 투표는 더 없다
        when(travelPlanPollMapper.findPollsBefore(
                PLAN_ID, 5L, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of());

        TravelPlanChatTimelineDto page = chatService.timeline(principal(), PLAN_ID, 10L, 5L);

        assertThat(page.nextBeforeMessageId()).isEqualTo(9L);
        // 기준이 사라지면 다음부터는 그 쪽을 읽지 않는다
        assertThat(page.nextBeforePollId()).isNull();

        chatService.timeline(principal(), PLAN_ID, 9L, null);
        // 두 번째 요청은 투표 쪽을 아예 읽지 않는다(위의 한 번이 전부다)
        verify(travelPlanPollMapper, times(1))
                .findPollsBefore(anyLong(), anyLong(), anyInt());
    }

    @Test
    void aFullPageOnEitherSideMeansThereIsMore() {
        givenRoom(TravelPlanRole.MEMBER);
        List<TravelPlanPoll> fullPage = new java.util.ArrayList<>();
        for (int index = 0; index < TravelPlanChatService.PAGE_SIZE; index++) {
            fullPage.add(pollAt(900L + index, "투표 " + index, 100 + index));
        }
        when(travelPlanChatMapper.findRecentMessages(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of());
        when(travelPlanPollMapper.findRecentPolls(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(fullPage);

        assertThat(chatService.timeline(principal(), PLAN_ID, null, null).hasMore()).isTrue();
    }

    @Test
    void whoeverLeftCannotReadTheHistory() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.timeline(principal(), PLAN_ID, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> chatService.unreadCount(principal(), PLAN_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nothingAboutThePollIsWrittenIntoTheChatTable() {
        // 알림은 읽을 때 합칠 뿐이다. 투표를 채팅 행으로 옮겨 적지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findRecentPolls(PLAN_ID, TravelPlanChatService.PAGE_SIZE))
                .thenReturn(List.of(pollAt(900L, "숙소 위치는?", 20)));

        chatService.timeline(principal(), PLAN_ID, null, null);

        verify(travelPlanChatMapper, never()).insertMessage(any());
    }

    @Test
    void aMessageFromSomeoneWhoLeftKeepsTheirRoomName() {
        // 나간 사람의 지난 메시지도 그때의 방 이름 그대로 보인다
        TravelPlanChatMessage past = message(1L, "먼저 갈게");
        past.setSenderDisplayName("쭈니");

        assertThat(TravelPlanChatMessageDto.of(past).displayName()).isEqualTo("쭈니");
    }

    // ── 안 읽은 개수 ────────────────────────────────────────

    @Test
    void unreadCountsOnlyWhatCameAfterTheReadMark() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findLastReadMessageId(PLAN_ID, MEMBER_ID)).thenReturn(1L);
        when(travelPlanChatMapper.countUnread(PLAN_ID, MEMBER_ID, 1L)).thenReturn(2);

        assertThat(chatService.unreadCount(principal(), PLAN_ID)).isEqualTo(2);
    }

    @Test
    void readingUpToTheLatestClearsTheCount() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findLastReadMessageId(PLAN_ID, MEMBER_ID)).thenReturn(1L);
        when(travelPlanChatMapper.countUnread(PLAN_ID, MEMBER_ID, 3L)).thenReturn(0);

        assertThat(chatService.markRead(principal(), PLAN_ID, 3L)).isZero();
        verify(travelPlanChatMapper).upsertReadPosition(PLAN_ID, MEMBER_ID, 3L);
    }

    @Test
    void theReadMarkNeverMovesBackwards() {
        // 스크롤할 때마다 같은 값을 다시 쓰지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findLastReadMessageId(PLAN_ID, MEMBER_ID)).thenReturn(5L);

        chatService.markRead(principal(), PLAN_ID, 3L);

        verify(travelPlanChatMapper, never()).upsertReadPosition(anyLong(), anyLong(), anyLong());
    }

    @Test
    void readingWithoutATargetMeansUpToTheLastMessage() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findLatestMessageId(PLAN_ID)).thenReturn(9L);
        when(travelPlanChatMapper.findLastReadMessageId(PLAN_ID, MEMBER_ID)).thenReturn(null);

        chatService.markRead(principal(), PLAN_ID, null);

        verify(travelPlanChatMapper).upsertReadPosition(PLAN_ID, MEMBER_ID, 9L);
    }

    @Test
    void anEmptyRoomHasNothingToRead() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanChatMapper.findLatestMessageId(PLAN_ID)).thenReturn(null);

        assertThat(chatService.markRead(principal(), PLAN_ID, null)).isZero();
        verify(travelPlanChatMapper, never()).upsertReadPosition(anyLong(), anyLong(), anyLong());
    }

    @Test
    void whoeverLeftCannotMarkAnythingRead() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> chatService.markRead(principal(), PLAN_ID, 3L))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanChatMapper, never()).upsertReadPosition(anyLong(), anyLong(), anyLong());
    }

    // ── 준비 ────────────────────────────────────────────────

    private void givenInsertSucceeds() {
        when(travelPlanChatMapper.insertMessage(any())).thenReturn(1);
    }

    private TravelPlanChatMessage captureInsert() {
        ArgumentCaptor<TravelPlanChatMessage> captor =
                ArgumentCaptor.forClass(TravelPlanChatMessage.class);
        verify(travelPlanChatMapper).insertMessage(captor.capture());
        return captor.getValue();
    }

    private TravelPlanChatChangedEvent captureEvent() {
        ArgumentCaptor<TravelPlanChatChangedEvent> captor =
                ArgumentCaptor.forClass(TravelPlanChatChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    /** @param second 순서를 눈에 보이게 하려고 초만 다르게 둔다 */
    private TravelPlanChatMessage messageAt(Long id, String content, int second) {
        TravelPlanChatMessage message = message(id, content);
        message.setCreatedAt(Timestamp.valueOf("2026-08-26 17:10:00"));
        message.setCreatedAt(new Timestamp(message.getCreatedAt().getTime() + second * 1000L));
        return message;
    }

    private TravelPlanPoll pollAt(Long id, String title, int second) {
        TravelPlanPoll poll = new TravelPlanPoll();
        poll.setId(id);
        poll.setTravelPlanId(PLAN_ID);
        poll.setCreatedByMemberId(MEMBER_ID);
        poll.setTitle(title);
        poll.setCreatedByDisplayName("민준");
        poll.setCreatedAt(new Timestamp(
                Timestamp.valueOf("2026-08-26 17:10:00").getTime() + second * 1000L));
        return poll;
    }

    private TravelPlanChatMessage message(Long id, String content) {
        TravelPlanChatMessage message = new TravelPlanChatMessage();
        message.setId(id);
        message.setTravelPlanId(PLAN_ID);
        message.setSenderMemberId(MEMBER_ID);
        message.setMessageType(TravelPlanChatMessageType.USER);
        message.setContent(content);
        message.setSenderDisplayName("민준");
        message.setCreatedAt(Timestamp.valueOf("2026-08-24 17:10:00"));
        return message;
    }

    private TravelPlanChatMessage messageOf(Long senderMemberId, Timestamp deletedAt) {
        TravelPlanChatMessage message = message(MESSAGE_ID, "숙소 어디가 좋을까?");
        message.setSenderMemberId(senderMemberId);
        message.setDeletedAt(deletedAt);
        return message;
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenRoom(TravelPlanRole role) {
        givenActivePlan();
        TravelPlanMember member = new TravelPlanMember();
        member.setId(MEMBER_ID);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setDisplayName("민준");
        member.setRole(role);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(member);
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
}
