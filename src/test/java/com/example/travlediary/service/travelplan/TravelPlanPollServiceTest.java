package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanPollCountsDto;
import com.example.travlediary.dto.TravelPlanPollCreateForm;
import com.example.travlediary.dto.TravelPlanPollDetailDto;
import com.example.travlediary.dto.TravelPlanPollDto;
import com.example.travlediary.dto.TravelPlanPollEventDto;
import com.example.travlediary.dto.TravelPlanPollSummaryDto;
import com.example.travlediary.model.TravelPlanPollOptionVoteCount;
import com.example.travlediary.model.TravelPlanPollVote;
import com.example.travlediary.model.TravelPlanPollVotedCount;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.model.TravelPlanPollCloseReason;
import com.example.travlediary.model.TravelPlanPollCloseType;
import com.example.travlediary.model.TravelPlanPollOption;
import com.example.travlediary.model.TravelPlanPollResultVisibility;
import com.example.travlediary.model.TravelPlanPollSelectionType;
import com.example.travlediary.model.TravelPlanPollStatus;
import com.example.travlediary.model.TravelPlanPollStatusCount;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.travelplan.TravelPlanAlternativeMapper;
import com.example.travlediary.repository.travelplan.TravelPlanItemMapper;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import com.example.travlediary.repository.travelplan.TravelPlanPollMapper;
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
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 채팅창에서 만드는 방 투표.
 *
 * <p>OWNER 와 MEMBER 가 똑같이 만들 수 있고, 만든 사람은 언제나 서버가 정한다.
 * 투표와 선택지는 한 트랜잭션이라 선택지 하나라도 실패하면 투표도 남지 않는다.
 * 실제 투표 참여·집계는 이번 단계에 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelPlanPollServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final Long MEMBER_ID = 11L;
    private static final Long POLL_ID = 900L;
    private static final Long OTHER_MEMBER_ID = 12L;
    private static final Long VOTE_ID = 700L;
    private static final Long OPTION_A = 1L;
    private static final Long OPTION_B = 2L;

    @Mock
    private TravelPlanPollMapper travelPlanPollMapper;
    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private TravelPlanAlternativeMapper travelPlanAlternativeMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TravelPlanPollService pollService;

    @BeforeEach
    void setUp() {
        TravelPlanRoomAccess roomAccess = new TravelPlanRoomAccess(
                travelPlanMapper, travelPlanItemMapper, travelPlanAlternativeMapper);
        pollService = new TravelPlanPollService(
                travelPlanPollMapper, travelPlanMapper, roomAccess, eventPublisher);
    }

    // ── 만들 수 있는 사람 ───────────────────────────────────

    @Test
    void anActiveOwnerCanCreateAPoll() {
        givenRoom(TravelPlanRole.OWNER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        assertThat(capturePoll().getTitle()).isEqualTo("숙소 위치는?");
    }

    @Test
    void anActiveMemberCanCreateOneToo() {
        // 방장 전용 기능이 아니다
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        assertThat(capturePoll().getCreatedByMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void whoeverLeftCannotCreateAPoll() {
        // ACTIVE 조건이 걸린 조회라 LEFT 는 여기서 비어 온다
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void whoeverWasRemovedCannotCreateAPoll() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    @Test
    void someoneWhoNeverJoinedCannotCreateAPoll() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aFinishedRoomTakesNoNewPolls() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    // ── 질문 ────────────────────────────────────────────────

    @Test
    void aBlankQuestionIsRefused() {
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("   \n ", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("투표 질문을 입력해 주세요.");
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    @Test
    void aQuestionLongerThanTheColumnIsRefusedBeforeTheDatabaseSeesIt() {
        // title 이 varchar(200) 이다. SQL 오류로 검증하지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        String tooLong = "가".repeat(TravelPlanPollService.MAX_QUESTION_LENGTH + 1);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form(tooLong, "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    // ── 선택지 ──────────────────────────────────────────────

    @Test
    void oneOptionIsNotAChoice() {
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시")))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    @Test
    void twoOptionsAreEnoughAndKeepTheirOrder() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        // display_order 는 1 부터 올라간다(DB CHECK 가 1 미만을 막는다)
        assertThat(captureOptions())
                .extracting(TravelPlanPollOption::getContent,
                        TravelPlanPollOption::getDisplayOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("제주시", 1),
                        org.assertj.core.groups.Tuple.tuple("서귀포시", 2));
    }

    @Test
    void tooManyOptionsAreRefused() {
        givenRoom(TravelPlanRole.MEMBER);
        String[] options = new String[TravelPlanPollService.MAX_OPTIONS + 1];
        for (int index = 0; index < options.length; index++) {
            options[index] = "선택지 " + index;
        }

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", options)))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    @Test
    void theMaximumItselfIsAccepted() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();
        String[] options = new String[TravelPlanPollService.MAX_OPTIONS];
        for (int index = 0; index < options.length; index++) {
            options[index] = "선택지 " + index;
        }

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", options));

        assertThat(captureOptions()).hasSize(TravelPlanPollService.MAX_OPTIONS);
    }

    @Test
    void emptyRowsAreDroppedRatherThanSaved() {
        // 화면에서 줄을 지우지 않고 비워 두었을 수 있다
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID,
                form("숙소 위치는?", "SINGLE", "제주시", "  ", "서귀포시", ""));

        assertThat(captureOptions())
                .extracting(TravelPlanPollOption::getContent)
                .containsExactly("제주시", "서귀포시");
    }

    @Test
    void blankOptionsAloneCannotMakeAPoll() {
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "   ")))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    @Test
    void theSameOptionTwiceIsRefused() {
        // 같은 선택지가 두 개면 어느 쪽을 고른 것인지 알 수 없다
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", " 제주시 ")))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("같은 선택지");
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    // ── 선택 방식 ───────────────────────────────────────────

    @Test
    void singleIsStoredAsSingle() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        assertThat(capturePoll().getSelectionType())
                .isEqualTo(TravelPlanPollSelectionType.SINGLE);
    }

    @Test
    void multipleIsStoredAsMultiple() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID,
                form("가고 싶은 곳은?", "MULTIPLE", "성산일출봉", "우도"));

        assertThat(capturePoll().getSelectionType())
                .isEqualTo(TravelPlanPollSelectionType.MULTIPLE);
    }

    @Test
    void anUnknownSelectionTypeIsRefused() {
        // 화면이 보낸 값을 그대로 믿지 않는다
        givenRoom(TravelPlanRole.MEMBER);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "RANKED", "제주시", "서귀포시")))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    // ── 서버가 정하는 값 ────────────────────────────────────

    @Test
    void theCreatorAndTheRestAreDecidedByTheServer() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        TravelPlanPollDto created = pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        TravelPlanPoll saved = capturePoll();
        assertThat(saved.getTravelPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getCreatedByMemberId()).isEqualTo(MEMBER_ID);
        // 고르게 하지 않는 값은 정해진 대로만 들어간다
        assertThat(saved.getStatus()).isEqualTo(TravelPlanPollStatus.OPEN);
        assertThat(saved.getResultVisibility())
                .isEqualTo(TravelPlanPollResultVisibility.REALTIME);
        assertThat(saved.getCloseType()).isEqualTo(TravelPlanPollCloseType.ALL_VOTED);
        assertThat(saved.getDeadlineAt()).isNull();
        // 이름도 방 안의 표시 이름에서만 나온다
        assertThat(created.createdByDisplayName()).isEqualTo("민준");
    }

    @Test
    void whatGoesOutCarriesNoAccountInformation() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        TravelPlanPollDto created = pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        assertThat(created.toString())
                .contains("민준")
                .doesNotContain("minjun")
                .doesNotContain("@")
                .doesNotContain("userId");
    }

    @Test
    void creatingAPollCountsAsActivityInTheRoom() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void noChatRowIsCreatedAlongTheWay() {
        // 채팅창에서 만들지만 표는 따로다. 가짜 채팅 메시지를 함께 만들지 않는다
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        assertThat(TravelPlanPollService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .noneMatch(type -> type.getSimpleName().contains("Chat"));
    }

    // ── 한 덩어리로 저장한다 ────────────────────────────────

    @Test
    void aFailedOptionTakesThePollDownWithIt() {
        // 선택지 하나가 실패했는데 투표만 남으면 고를 것이 없는 투표가 된다
        givenRoom(TravelPlanRole.MEMBER);
        givenPollInsertSucceeds();
        when(travelPlanPollMapper.insertOption(any())).thenReturn(1).thenReturn(0);

        assertThatThrownBy(() -> pollService.createPoll(
                principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시")))
                .isInstanceOf(ResponseStatusException.class);
        // 예외로 빠져나가 트랜잭션이 되돌려진다. 알림도 나가지 않는다
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theWholeCreationIsOneTransaction() throws NoSuchMethodException {
        Method create = TravelPlanPollService.class.getMethod(
                "createPoll", Principal.class, Long.class, TravelPlanPollCreateForm.class);

        assertThat(create.isAnnotationPresent(
                org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }

    // ── 커밋 뒤에만 나간다 ──────────────────────────────────

    @Test
    void theRoomHearsAboutItOnlyAfterItIsSaved() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID, form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        TravelPlanPollChangedEvent event = captureEvent();
        assertThat(event.travelPlanId()).isEqualTo(PLAN_ID);
        assertThat(event.payload().type()).isEqualTo(TravelPlanPollEventDto.POLL_CREATED);
        assertThat(event.payload().poll().title()).isEqualTo("숙소 위치는?");
        assertThat(event.payload().poll().options())
                .extracting(option -> option.content())
                .containsExactly("제주시", "서귀포시");
    }

    @Test
    void theServiceNeverTalksToWebSocketItself() {
        // Service 가 SimpMessagingTemplate 을 들고 있으면 롤백된 투표가 먼저 나갈 수 있다
        assertThat(TravelPlanPollService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(
                        org.springframework.messaging.simp.SimpMessagingTemplate.class);
    }

    @Test
    void theListenerOnlySendsAfterTheTransactionCommits() throws NoSuchMethodException {
        Method listener = TravelPlanPollChangedListener.class.getMethod(
                "onPollChanged", TravelPlanPollChangedEvent.class);

        assertThat(listener.getAnnotation(
                org.springframework.transaction.event.TransactionalEventListener.class).phase())
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    }

    // ── 진행 중인 투표 조회 ─────────────────────────────────

    @Test
    void theRunningListStaysCompactAndCountsWhoTookPart() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findOpenPolls(PLAN_ID)).thenReturn(List.of(openPoll()));
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(3);
        when(travelPlanPollMapper.countVotedMembersByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(votedCount(POLL_ID, 2)));

        List<TravelPlanPollSummaryDto> polls = pollService.openPolls(principal(), PLAN_ID);

        assertThat(polls).hasSize(1);
        assertThat(polls.get(0).title()).isEqualTo("숙소 위치는?");
        assertThat(polls.get(0).createdByDisplayName()).isEqualTo("민준");
        assertThat(polls.get(0).votedMemberCount()).isEqualTo(2);
        assertThat(polls.get(0).activeMemberCount()).isEqualTo(3);
        // 진행 중 목록에서는 선택지도 표도 읽지 않는다
        verify(travelPlanPollMapper, never()).findOptionsByPollIds(any());
        verify(travelPlanPollMapper, never()).countSelectionsByPollIds(any());
    }

    @Test
    void aPollNobodyVotedOnShowsZero() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findOpenPolls(PLAN_ID)).thenReturn(List.of(openPoll()));
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(3);
        // 아무도 투표하지 않으면 집계에 아예 나오지 않는다
        when(travelPlanPollMapper.countVotedMembersByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of());

        assertThat(pollService.openPolls(principal(), PLAN_ID).get(0).votedMemberCount())
                .isZero();
    }

    @Test
    void finishedPollsComeFromTheirOwnStatusNotFromMadeUpData() {
        // 마감 기능이 아직 없어 지금은 비어 있는 것이 정상이다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findClosedPolls(PLAN_ID)).thenReturn(List.of());

        assertThat(pollService.closedPolls(principal(), PLAN_ID)).isEmpty();
        verify(travelPlanPollMapper).findClosedPolls(PLAN_ID);
    }

    @Test
    void whoeverLeftCannotSeeTheFinishedPollsEither() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.closedPolls(principal(), PLAN_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).findClosedPolls(anyLong());
    }

    @Test
    void anEmptyRoomAsksForNoOptions() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findOpenPolls(PLAN_ID)).thenReturn(List.of());

        assertThat(pollService.openPolls(principal(), PLAN_ID)).isEmpty();
        verify(travelPlanPollMapper, never()).findOptionsByPollIds(any());
    }

    // ── 탭 숫자 ─────────────────────────────────────────────

    @Test
    void bothNumbersAreKnownWithoutReadingEitherList() {
        // 지난 투표가 둘 있고 진행 중인 것은 없다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.countPollsByStatus(PLAN_ID))
                .thenReturn(List.of(statusCount(TravelPlanPollStatus.CLOSED, 2)));

        TravelPlanPollCountsDto counts = pollService.pollCounts(principal(), PLAN_ID);

        assertThat(counts.open()).isZero();
        assertThat(counts.closed()).isEqualTo(2);
        // 숫자를 얻자고 목록을 통째로 읽지 않는다
        verify(travelPlanPollMapper, never()).findClosedPolls(anyLong());
        verify(travelPlanPollMapper, never()).findOpenPolls(anyLong());
    }

    @Test
    void bothNumbersComeBackTogether() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.countPollsByStatus(PLAN_ID))
                .thenReturn(List.of(statusCount(TravelPlanPollStatus.OPEN, 1),
                        statusCount(TravelPlanPollStatus.CLOSED, 2)));

        TravelPlanPollCountsDto counts = pollService.pollCounts(principal(), PLAN_ID);

        assertThat(counts.open()).isEqualTo(1);
        assertThat(counts.closed()).isEqualTo(2);
    }

    @Test
    void aStateThatNeverHappenedCountsAsZero() {
        // 한 번도 나오지 않은 상태는 집계에 아예 없다
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.countPollsByStatus(PLAN_ID)).thenReturn(List.of());

        TravelPlanPollCountsDto counts = pollService.pollCounts(principal(), PLAN_ID);

        assertThat(counts.open()).isZero();
        assertThat(counts.closed()).isZero();
    }

    @Test
    void whoeverLeftCannotSeeTheNumbersEither() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.pollCounts(principal(), PLAN_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).countPollsByStatus(anyLong());
    }

    @Test
    void whoeverLeftCannotSeeTheOpenPolls() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.openPolls(principal(), PLAN_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).findOpenPolls(anyLong());
    }

    // ── 투표하기 ────────────────────────────────────────────

    @Test
    void oneChoiceIsStoredForASingleChoicePoll() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        verify(travelPlanPollMapper).insertVote(any());
        verify(travelPlanPollMapper).insertSelection(VOTE_ID, OPTION_A);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void twoChoicesAreRefusedForASingleChoicePoll() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A, OPTION_B)))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("하나만");
        verify(travelPlanPollMapper, never()).insertVote(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void severalChoicesAreFineForAMultipleChoicePoll() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.MULTIPLE);
        givenNoVoteYet();

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A, OPTION_B));

        verify(travelPlanPollMapper).insertSelection(VOTE_ID, OPTION_A);
        verify(travelPlanPollMapper).insertSelection(VOTE_ID, OPTION_B);
    }

    @Test
    void choosingNothingIsRefused() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.MULTIPLE);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of()))
                .isInstanceOf(TravelPlanValidationException.class);
        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, null))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertVote(any());
    }

    @Test
    void theSameChoiceSentTwiceCountsOnce() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.MULTIPLE);
        givenNoVoteYet();

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A, OPTION_A));

        verify(travelPlanPollMapper, times(1)).insertSelection(VOTE_ID, OPTION_A);
    }

    @Test
    void aChoiceFromAnotherPollIsRefused() {
        // 화면이 보낸 번호를 믿지 않는다. 그 투표의 것인지 서버가 다시 본다
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.MULTIPLE);
        when(travelPlanPollMapper.countOwnedOptions(POLL_ID, List.of(OPTION_A, 999L)))
                .thenReturn(1);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A, 999L)))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertVote(any());
    }

    @Test
    void changingMyChoiceMovesTheVoteWithoutAddingAPerson() {
        // 사람마다 투표 줄은 하나다. 이전 선택만 갈아 끼운다
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        TravelPlanPollVote existing = new TravelPlanPollVote();
        existing.setId(VOTE_ID);
        existing.setPollId(POLL_ID);
        existing.setMemberId(MEMBER_ID);
        when(travelPlanPollMapper.findVoteByPollAndMember(POLL_ID, MEMBER_ID))
                .thenReturn(existing);
        when(travelPlanPollMapper.insertSelection(anyLong(), anyLong())).thenReturn(1);

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_B));

        // 투표 줄을 새로 만들지 않는다 -> 참여 인원은 그대로다
        verify(travelPlanPollMapper, never()).insertVote(any());
        // 이전 선택을 걷어 내고 새 선택만 넣는다 -> 표만 옮겨 간다
        verify(travelPlanPollMapper).deleteSelectionsByVoteId(VOTE_ID);
        verify(travelPlanPollMapper).insertSelection(VOTE_ID, OPTION_B);
        verify(travelPlanPollMapper, never()).insertSelection(VOTE_ID, OPTION_A);
    }

    @Test
    void aFinishedPollTakesNoMoreVotes() {
        givenRoom(TravelPlanRole.MEMBER);
        TravelPlanPoll poll = openPoll();
        poll.setStatus(TravelPlanPollStatus.CLOSED);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(poll);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A)))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("이미 끝난");
        verify(travelPlanPollMapper, never()).insertVote(any());
    }

    @Test
    void aPollFromAnotherRoomCannotBeVotedOn() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(null);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void whoeverLeftOrWasRemovedCannotVote() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A)))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).insertVote(any());
    }

    @Test
    void aFinishedRoomTakesNoVotes() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.submitVote(
                principal(), PLAN_ID, POLL_ID, List.of(OPTION_A)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theRoomHearsAboutAVoteOnlyAfterItIsSaved() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        TravelPlanPollChangedEvent event = captureEvent();
        assertThat(event.payload().type()).isEqualTo(TravelPlanPollEventDto.POLL_VOTED);
        assertThat(event.payload().pollId()).isEqualTo(POLL_ID);
        // 숫자는 싣지 않는다. 화면이 스스로 표를 올리지 않고 서버에서 다시 읽는다
        assertThat(event.payload().poll()).isNull();
    }

    // ── 상세 ────────────────────────────────────────────────

    @Test
    void theDetailCarriesTheCountsAndMyOwnChoice() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(openPoll());
        when(travelPlanPollMapper.findOptionsByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(option("제주시", 1), option("서귀포시", 2)));
        when(travelPlanPollMapper.countSelectionsByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(optionCount(1L, 2)));
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(2);
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(3);
        TravelPlanPollVote mine = new TravelPlanPollVote();
        mine.setId(VOTE_ID);
        when(travelPlanPollMapper.findVoteByPollAndMember(POLL_ID, MEMBER_ID)).thenReturn(mine);
        when(travelPlanPollMapper.findSelectedOptionIds(VOTE_ID)).thenReturn(List.of(1L));

        TravelPlanPollDetailDto detail = pollService.pollDetail(principal(), PLAN_ID, POLL_ID);

        assertThat(detail.votedMemberCount()).isEqualTo(2);
        assertThat(detail.activeMemberCount()).isEqualTo(3);
        assertThat(detail.selectedOptionIds()).containsExactly(1L);
        // 표를 못 받은 선택지는 집계에 없다. 0 으로 채워 준다
        assertThat(detail.options())
                .extracting(option -> option.content(), option -> option.voteCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("제주시", 2),
                        org.assertj.core.groups.Tuple.tuple("서귀포시", 0));
        // 누가 무엇을 골랐는지는 담지 않는다
        assertThat(detail.toString()).doesNotContain("민준님").doesNotContain("userId");
    }

    @Test
    void someoneWhoHasNotVotedHasNothingChecked() {
        givenRoom(TravelPlanRole.MEMBER);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(openPoll());
        when(travelPlanPollMapper.findVoteByPollAndMember(POLL_ID, MEMBER_ID)).thenReturn(null);

        assertThat(pollService.pollDetail(principal(), PLAN_ID, POLL_ID).selectedOptionIds())
                .isEmpty();
    }

    @Test
    void whoeverLeftCannotOpenAPoll() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.pollDetail(principal(), PLAN_ID, POLL_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 끝난 투표의 결과 ────────────────────────────────────

    @Test
    void theOptionWithTheMostVotesIsTheResult() {
        givenClosedPollWith(List.of(optionCount(1L, 3), optionCount(2L, 1)));

        assertThat(pollService.closedPolls(principal(), PLAN_ID).get(0).winnerSummary())
                .isEqualTo("제주시");
    }

    @Test
    void aTieIsShownAsATieRatherThanPickingOne() {
        givenClosedPollWith(List.of(optionCount(1L, 2), optionCount(2L, 2)));

        assertThat(pollService.closedPolls(principal(), PLAN_ID).get(0).winnerSummary())
                .isEqualTo("제주시 · 서귀포시 공동 1위");
    }

    @Test
    void aPollNobodyVotedOnHasNoResult() {
        givenClosedPollWith(List.of());

        assertThat(pollService.closedPolls(principal(), PLAN_ID).get(0).winnerSummary())
                .isEqualTo("투표 결과 없음");
    }

    @Test
    void theFinishedListDoesNotCarryTheOptionsThemselves() {
        givenClosedPollWith(List.of(optionCount(1L, 3)));

        // 목록에는 결과 한 줄만 나간다
        assertThat(pollService.closedPolls(principal(), PLAN_ID).get(0).toString())
                .doesNotContain("서귀포시");
    }

    // ── 마감: 직접 ──────────────────────────────────────────

    @Test
    void theCreatorCanCloseTheirOwnPoll() {
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> poll.setCloseType(TravelPlanPollCloseType.MANUAL));
        when(travelPlanPollMapper.closePoll(POLL_ID, TravelPlanPollCloseReason.MANUAL))
                .thenReturn(1);

        pollService.closePoll(principal(), PLAN_ID, POLL_ID);

        verify(travelPlanPollMapper).closePoll(POLL_ID, TravelPlanPollCloseReason.MANUAL);
        assertThat(captureEvent().payload().type())
                .isEqualTo(TravelPlanPollEventDto.POLL_CLOSED);
    }

    @Test
    void anotherMemberCannotCloseSomeoneElsesPoll() {
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> poll.setCreatedByMemberId(OTHER_MEMBER_ID));

        assertThatThrownBy(() -> pollService.closePoll(principal(), PLAN_ID, POLL_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).closePoll(anyLong(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void beingTheOwnerDoesNotAllowClosingSomeoneElsesPoll() {
        givenRoom(TravelPlanRole.OWNER);
        givenPoll(poll -> poll.setCreatedByMemberId(OTHER_MEMBER_ID));

        assertThatThrownBy(() -> pollService.closePoll(principal(), PLAN_ID, POLL_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(travelPlanPollMapper, never()).closePoll(anyLong(), any());
    }

    @Test
    void anAlreadyFinishedPollIsNotClosedAgain() {
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> poll.setStatus(TravelPlanPollStatus.CLOSED));

        assertThatThrownBy(() -> pollService.closePoll(principal(), PLAN_ID, POLL_ID))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).closePoll(anyLong(), any());
    }

    @Test
    void whoeverLeftCannotCloseAPoll() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> pollService.closePoll(principal(), PLAN_ID, POLL_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void losingTheRaceMeansNoSecondAnnouncement() {
        // 다른 쪽이 먼저 마감했다. 조건부 UPDATE 가 0 을 돌려준다
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> poll.setCloseType(TravelPlanPollCloseType.MANUAL));
        when(travelPlanPollMapper.closePoll(POLL_ID, TravelPlanPollCloseReason.MANUAL))
                .thenReturn(0);

        pollService.closePoll(principal(), PLAN_ID, POLL_ID);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ── 마감: 전원 투표 ─────────────────────────────────────

    @Test
    void aPollStaysOpenUntilEveryoneHasVoted() {
        // 참여자 4명 중 3명만 투표한 상태
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(4);
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(3);

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        verify(travelPlanPollMapper, never()).closePoll(anyLong(), any());
    }

    @Test
    void theLastVoteEndsThePoll() {
        // 참여자 4명이 모두 투표했다
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(4);
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(4);
        when(travelPlanPollMapper.closePoll(POLL_ID, TravelPlanPollCloseReason.ALL_VOTED))
                .thenReturn(1);

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        verify(travelPlanPollMapper).closePoll(POLL_ID, TravelPlanPollCloseReason.ALL_VOTED);
        // 투표했다는 알림과 끝났다는 알림이 함께 나간다
        assertThat(captureEvents())
                .extracting(event -> event.payload().type())
                .containsExactly(TravelPlanPollEventDto.POLL_VOTED,
                        TravelPlanPollEventDto.POLL_CLOSED);
    }

    @Test
    void theCountThatMattersIsWhoIsInTheRoomNow() {
        /*
          초대 정원(8명)이 아니라 지금 남아 있는 사람이 기준이다.
          누가 나가서 남은 사람이 모두 투표한 상태가 되면 그때 끝난다.
        */
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(2);
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(2);
        when(travelPlanPollMapper.closePoll(POLL_ID, TravelPlanPollCloseReason.ALL_VOTED))
                .thenReturn(1);

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        verify(travelPlanPollMapper).closePoll(POLL_ID, TravelPlanPollCloseReason.ALL_VOTED);
    }

    @Test
    void anAlreadyFinishedPollIsNotClosedAgainByTheLastVote() {
        givenOpenPollToVoteOn(TravelPlanPollSelectionType.SINGLE);
        givenNoVoteYet();
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(4);
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(4);
        // 그 사이 만든 사람이 먼저 마감했다
        when(travelPlanPollMapper.closePoll(POLL_ID, TravelPlanPollCloseReason.ALL_VOTED))
                .thenReturn(0);

        pollService.submitVote(principal(), PLAN_ID, POLL_ID, List.of(OPTION_A));

        // 끝났다는 알림은 나가지 않는다. 투표했다는 알림 하나뿐이다
        assertThat(captureEvents())
                .extracting(event -> event.payload().type())
                .containsExactly(TravelPlanPollEventDto.POLL_VOTED);
    }

    // ── 만들 때 정해지는 마감 규칙 ──────────────────────────

    @Test
    void everyNewPollEndsTheSameWay() {
        // 마감 방식을 고르게 하지 않는다. 화면이 무엇을 보내든 규칙은 하나다
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();

        pollService.createPoll(principal(), PLAN_ID,
                form("숙소 위치는?", "SINGLE", "제주시", "서귀포시"));

        TravelPlanPoll saved = capturePoll();
        assertThat(saved.getCloseType()).isEqualTo(TravelPlanPollCloseType.ALL_VOTED);
        // 시각으로 끝나는 투표는 없다
        assertThat(saved.getDeadlineAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo(TravelPlanPollStatus.OPEN);
    }

    @Test
    void theFormNoLongerCarriesAWayToEndThePoll() {
        // 프론트와 주고받는 값에서 마감 방식이 아예 빠졌다
        assertThat(TravelPlanPollCreateForm.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder(
                        "question", "selectionType", "options", "resultVisibility");
    }

    @Test
    void howResultsAreSharedIsStillUpToTheAuthor() {
        givenRoom(TravelPlanRole.MEMBER);
        givenInsertsSucceed();
        TravelPlanPollCreateForm form = form("숙소 위치는?", "SINGLE", "제주시", "서귀포시");
        form.setResultVisibility("AFTER_CLOSE");

        pollService.createPoll(principal(), PLAN_ID, form);

        assertThat(capturePoll().getResultVisibility())
                .isEqualTo(TravelPlanPollResultVisibility.AFTER_CLOSE);
    }

    @Test
    void anUnknownVisibilityIsRefused() {
        givenRoom(TravelPlanRole.MEMBER);
        TravelPlanPollCreateForm badVisibility = form("질문", "SINGLE", "가", "나");
        badVisibility.setResultVisibility("SOMETIMES");

        assertThatThrownBy(() -> pollService.createPoll(principal(), PLAN_ID, badVisibility))
                .isInstanceOf(TravelPlanValidationException.class);
        verify(travelPlanPollMapper, never()).insertPoll(any());
    }

    // ── 결과 공개 시점 ──────────────────────────────────────

    @Test
    void aRealtimePollShowsItsVotesWhileItRuns() {
        givenDetailOf(TravelPlanPollResultVisibility.REALTIME, TravelPlanPollStatus.OPEN);

        TravelPlanPollDetailDto detail = pollService.pollDetail(principal(), PLAN_ID, POLL_ID);

        assertThat(detail.resultsVisible()).isTrue();
        assertThat(detail.options()).extracting(option -> option.voteCount())
                .containsExactly(2, 0);
    }

    @Test
    void aPollThatWaitsKeepsItsVotesOffTheWireUntilItEnds() {
        givenDetailOf(TravelPlanPollResultVisibility.AFTER_CLOSE, TravelPlanPollStatus.OPEN);

        TravelPlanPollDetailDto detail = pollService.pollDetail(principal(), PLAN_ID, POLL_ID);

        assertThat(detail.resultsVisible()).isFalse();
        // 0 으로 내리면 "아무도 안 골랐다" 로 읽힌다. 아예 담지 않는다
        assertThat(detail.options()).extracting(option -> option.voteCount())
                .containsOnlyNulls();
        assertThat(detail.winnerSummary()).isNull();
        // 표는 가려도 참여 인원과 내 선택은 보인다
        assertThat(detail.votedMemberCount()).isEqualTo(2);
        assertThat(detail.selectedOptionIds()).containsExactly(1L);
        // 가릴 것이라면 세지도 않는다
        verify(travelPlanPollMapper, never()).countSelectionsByPollIds(any());
    }

    @Test
    void onceItEndsTheWaitingPollShowsEverything() {
        givenDetailOf(TravelPlanPollResultVisibility.AFTER_CLOSE, TravelPlanPollStatus.CLOSED);

        TravelPlanPollDetailDto detail = pollService.pollDetail(principal(), PLAN_ID, POLL_ID);

        assertThat(detail.resultsVisible()).isTrue();
        assertThat(detail.options()).extracting(option -> option.voteCount())
                .containsExactly(2, 0);
        assertThat(detail.winnerSummary()).isEqualTo("제주시");
    }

    @Test
    void theCloseActionIsOfferedToTheCreatorOfAnyRunningPoll() {
        // 전원이 투표하기를 기다릴 필요 없이 언제든 끝낼 수 있다
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> { });

        assertThat(pollService.pollDetail(principal(), PLAN_ID, POLL_ID).closable()).isTrue();

        // 남의 투표에는 나오지 않는다
        givenPoll(poll -> poll.setCreatedByMemberId(OTHER_MEMBER_ID));
        assertThat(pollService.pollDetail(principal(), PLAN_ID, POLL_ID).closable()).isFalse();

        // 이미 끝난 투표에도 나오지 않는다
        givenPoll(poll -> poll.setStatus(TravelPlanPollStatus.CLOSED));
        assertThat(pollService.pollDetail(principal(), PLAN_ID, POLL_ID).closable()).isFalse();
    }

    // ── 준비 ────────────────────────────────────────────────

    private void givenPoll(java.util.function.Consumer<TravelPlanPoll> customise) {
        TravelPlanPoll poll = openPoll();
        customise.accept(poll);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(poll);
    }

    private void givenDetailOf(TravelPlanPollResultVisibility visibility,
                               TravelPlanPollStatus status) {
        givenRoom(TravelPlanRole.MEMBER);
        givenPoll(poll -> {
            poll.setResultVisibility(visibility);
            poll.setStatus(status);
        });
        when(travelPlanPollMapper.findOptionsByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(option("제주시", 1), option("서귀포시", 2)));
        when(travelPlanPollMapper.countSelectionsByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(optionCount(1L, 2)));
        when(travelPlanPollMapper.countVotedMembers(POLL_ID)).thenReturn(2);
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(3);
        TravelPlanPollVote mine = new TravelPlanPollVote();
        mine.setId(VOTE_ID);
        when(travelPlanPollMapper.findVoteByPollAndMember(POLL_ID, MEMBER_ID)).thenReturn(mine);
        when(travelPlanPollMapper.findSelectedOptionIds(VOTE_ID)).thenReturn(List.of(1L));
    }

    private List<TravelPlanPollChangedEvent> captureEvents() {
        ArgumentCaptor<TravelPlanPollChangedEvent> captor =
                ArgumentCaptor.forClass(TravelPlanPollChangedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce())
                .publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private void givenOpenPollToVoteOn(TravelPlanPollSelectionType selectionType) {
        givenOpenPollToVoteOn(selectionType, TravelPlanPollCloseType.MANUAL);
    }

    private void givenOpenPollToVoteOn(TravelPlanPollSelectionType selectionType,
                                       TravelPlanPollCloseType closeType) {
        givenRoom(TravelPlanRole.MEMBER);
        TravelPlanPoll poll = openPoll();
        poll.setSelectionType(selectionType);
        poll.setCloseType(closeType);
        when(travelPlanPollMapper.findByIdAndPlanId(POLL_ID, PLAN_ID)).thenReturn(poll);
        when(travelPlanPollMapper.countOptionsByPollId(POLL_ID)).thenReturn(2);
        when(travelPlanPollMapper.countOwnedOptions(anyLong(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1, List.class).size());
    }

    private void givenNoVoteYet() {
        when(travelPlanPollMapper.findVoteByPollAndMember(POLL_ID, MEMBER_ID)).thenReturn(null);
        when(travelPlanPollMapper.insertVote(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, TravelPlanPollVote.class).setId(VOTE_ID);
            return 1;
        });
        when(travelPlanPollMapper.insertSelection(anyLong(), anyLong())).thenReturn(1);
    }

    private void givenClosedPollWith(List<TravelPlanPollOptionVoteCount> counts) {
        givenRoom(TravelPlanRole.MEMBER);
        TravelPlanPoll poll = openPoll();
        poll.setStatus(TravelPlanPollStatus.CLOSED);
        when(travelPlanPollMapper.findClosedPolls(PLAN_ID)).thenReturn(List.of(poll));
        when(travelPlanMapper.countActiveMembers(PLAN_ID)).thenReturn(3);
        when(travelPlanPollMapper.countVotedMembersByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of());
        when(travelPlanPollMapper.findOptionsByPollIds(List.of(POLL_ID)))
                .thenReturn(List.of(option("제주시", 1), option("서귀포시", 2)));
        when(travelPlanPollMapper.countSelectionsByPollIds(List.of(POLL_ID)))
                .thenReturn(counts);
    }

    private TravelPlanPollVotedCount votedCount(Long pollId, int count) {
        TravelPlanPollVotedCount voted = new TravelPlanPollVotedCount();
        voted.setPollId(pollId);
        voted.setVotedMemberCount(count);
        return voted;
    }

    private TravelPlanPollStatusCount statusCount(TravelPlanPollStatus status, int count) {
        TravelPlanPollStatusCount statusCount = new TravelPlanPollStatusCount();
        statusCount.setStatus(status);
        statusCount.setPollCount(count);
        return statusCount;
    }

    private TravelPlanPollOptionVoteCount optionCount(Long optionId, int count) {
        TravelPlanPollOptionVoteCount option = new TravelPlanPollOptionVoteCount();
        option.setPollId(POLL_ID);
        option.setOptionId(optionId);
        option.setVoteCount(count);
        return option;
    }

    private void givenPollInsertSucceeds() {
        when(travelPlanPollMapper.insertPoll(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, TravelPlanPoll.class).setId(POLL_ID);
            return 1;
        });
    }

    private void givenInsertsSucceed() {
        givenPollInsertSucceeds();
        when(travelPlanPollMapper.insertOption(any())).thenReturn(1);
    }

    private TravelPlanPoll capturePoll() {
        ArgumentCaptor<TravelPlanPoll> captor = ArgumentCaptor.forClass(TravelPlanPoll.class);
        verify(travelPlanPollMapper).insertPoll(captor.capture());
        return captor.getValue();
    }

    private List<TravelPlanPollOption> captureOptions() {
        ArgumentCaptor<TravelPlanPollOption> captor =
                ArgumentCaptor.forClass(TravelPlanPollOption.class);
        verify(travelPlanPollMapper, org.mockito.Mockito.atLeastOnce())
                .insertOption(captor.capture());
        return captor.getAllValues();
    }

    private TravelPlanPollChangedEvent captureEvent() {
        ArgumentCaptor<TravelPlanPollChangedEvent> captor =
                ArgumentCaptor.forClass(TravelPlanPollChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private TravelPlanPollCreateForm form(String question, String selectionType,
                                          String... options) {
        TravelPlanPollCreateForm form = new TravelPlanPollCreateForm();
        form.setQuestion(question);
        form.setSelectionType(selectionType);
        form.setOptions(java.util.Arrays.asList(options));
        return form;
    }

    private TravelPlanPoll openPoll() {
        TravelPlanPoll poll = new TravelPlanPoll();
        poll.setId(POLL_ID);
        poll.setTravelPlanId(PLAN_ID);
        poll.setCreatedByMemberId(MEMBER_ID);
        poll.setTitle("숙소 위치는?");
        poll.setSelectionType(TravelPlanPollSelectionType.SINGLE);
        poll.setStatus(TravelPlanPollStatus.OPEN);
        poll.setCreatedByDisplayName("민준");
        poll.setCreatedAt(Timestamp.valueOf("2026-08-26 17:10:00"));
        return poll;
    }

    private TravelPlanPollOption option(String content, int displayOrder) {
        TravelPlanPollOption option = new TravelPlanPollOption();
        option.setId((long) displayOrder);
        option.setPollId(POLL_ID);
        option.setContent(content);
        option.setDisplayOrder(displayOrder);
        return option;
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
