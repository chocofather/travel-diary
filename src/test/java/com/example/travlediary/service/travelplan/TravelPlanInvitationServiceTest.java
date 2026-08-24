package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanInvitePreviewDto;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanInvitation;
import com.example.travlediary.model.TravelPlanInvitationStatus;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanInvitationMapper;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 초대 링크 발급/재발급/비활성화와 미리보기.
 * OWNER 여부는 travel_plans.created_by_user_id 가 아니라
 * travel_plan_members(role=OWNER, status=ACTIVE)가 기준이다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanInvitationServiceTest {

    private static final Long OWNER_USER_ID = 7L;
    private static final Long MEMBER_USER_ID = 8L;
    private static final Long OUTSIDER_USER_ID = 9L;
    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final LocalDate START = LocalDate.of(2026, 9, 13);
    private static final LocalDate END = LocalDate.of(2026, 9, 15);

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanInvitationMapper travelPlanInvitationMapper;
    @InjectMocks
    private TravelPlanInvitationService travelPlanInvitationService;

    // ── 발급 ────────────────────────────────────────────────

    @Test
    void theOwnerGetsARawTokenBackWhileOnlyTheHashIsStored() {
        givenActiveOwner();
        givenNoActiveInvitation();
        when(travelPlanInvitationMapper.insertInvitation(any())).thenReturn(1);

        String rawToken = travelPlanInvitationService.createInvitation(OWNER_USER_ID, PLAN_ID);

        TravelPlanInvitation saved = captureInsert();
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTravelPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getCreatedByUserId()).isEqualTo(OWNER_USER_ID);
        assertThat(saved.getStatus()).isEqualTo(TravelPlanInvitationStatus.ACTIVE);
        // 저장되는 것은 해시뿐이다
        assertThat(saved.getTokenHash())
                .isEqualTo(TravelPlanInviteToken.hash(rawToken))
                .hasSize(64)
                .isNotEqualTo(rawToken);
        assertThat(saved.toString()).doesNotContain(rawToken);
    }

    @Test
    void aSecondCreateIsRefusedWhileALinkIsStillActive() {
        givenActiveOwner();
        when(travelPlanInvitationMapper.findActiveByPlanId(PLAN_ID, "ACTIVE"))
                .thenReturn(invitation());

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("재발급");

        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
    }

    @Test
    void aPlainMemberCannotManageInvitations() {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(2L);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(MEMBER_USER_ID);
        member.setRole(TravelPlanRole.MEMBER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID, "ACTIVE"))
                .thenReturn(member);

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanInvitationService.regenerateInvitation(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanInvitationService.disableInvitation(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
        verify(travelPlanInvitationMapper, never()).invalidateActiveInvitation(
                anyLong(), anyString(), anyString());
    }

    @Test
    void whoeverHandedTheRoomOverLosesTheInviteControls() {
        // 방장을 넘기고 나면 role 이 MEMBER 라 기존 OWNER 검증에서 그대로 막힌다.
        // 초대 Service 는 손대지 않았다.
        TravelPlanMember formerOwner = new TravelPlanMember();
        formerOwner.setId(1L);
        formerOwner.setTravelPlanId(PLAN_ID);
        formerOwner.setUserId(OWNER_USER_ID);
        formerOwner.setRole(TravelPlanRole.MEMBER);
        formerOwner.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, OWNER_USER_ID, "ACTIVE"))
                .thenReturn(formerOwner);

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanInvitationService.disableInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(travelPlanInvitationService.hasActiveInvitation(OWNER_USER_ID, PLAN_ID))
                .isFalse();

        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
        verify(travelPlanInvitationMapper, never()).invalidateActiveInvitation(
                anyLong(), anyString(), anyString());
    }

    @Test
    void anOwnerOfAnotherRoomCannotManageThisRoomsInvitations() {
        // 다른 방의 OWNER 라도 이 방에는 참여 기록이 없다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, OUTSIDER_USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(OUTSIDER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanInvitationService.regenerateInvitation(OUTSIDER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
    }

    @Test
    void aRoomThatIsNoLongerActiveCannotIssueLinks() {
        givenOwnerMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
    }

    @Test
    void aMissingLoginIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> travelPlanInvitationService.createInvitation(null, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("userId");

        verify(travelPlanMapper, never()).findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
    }

    @Test
    void aFailedInsertIsNotReportedAsASuccessfulLink() {
        givenActiveOwner();
        givenNoActiveInvitation();
        when(travelPlanInvitationMapper.insertInvitation(any())).thenReturn(0);

        assertThatThrownBy(() ->
                travelPlanInvitationService.createInvitation(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── 재발급 ──────────────────────────────────────────────

    @Test
    void regeneratingReplacesTheOldLinkBeforeIssuingANewOne() {
        givenActiveOwner();
        when(travelPlanInvitationMapper.insertInvitation(any())).thenReturn(1);

        String rawToken = travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID);

        // 기존 ACTIVE 를 REPLACED 로 끄고(그 시점에 invalidated_at 이 찍힌다) 새로 넣는다
        InOrder order = inOrder(travelPlanInvitationMapper);
        order.verify(travelPlanInvitationMapper).invalidateActiveInvitation(
                PLAN_ID, "ACTIVE", "REPLACED");
        order.verify(travelPlanInvitationMapper).insertInvitation(any());

        TravelPlanInvitation saved = captureInsert();
        assertThat(saved.getStatus()).isEqualTo(TravelPlanInvitationStatus.ACTIVE);
        assertThat(saved.getTokenHash()).isEqualTo(TravelPlanInviteToken.hash(rawToken));
    }

    @Test
    void theNewLinkNeverMatchesThePreviousOne() {
        givenActiveOwner();
        when(travelPlanInvitationMapper.insertInvitation(any())).thenReturn(1);

        String first = travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID);
        String second = travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID);

        assertThat(second).isNotEqualTo(first);
        assertThat(TravelPlanInviteToken.hash(second))
                .isNotEqualTo(TravelPlanInviteToken.hash(first));
    }

    @Test
    void theOldLinkStopsResolvingWhileTheNewOneWorks() {
        givenActiveOwner();
        when(travelPlanInvitationMapper.insertInvitation(any())).thenReturn(1);
        String oldToken = travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID);
        String newToken = travelPlanInvitationService.regenerateInvitation(OWNER_USER_ID, PLAN_ID);

        // 끊긴 링크는 상태 조건에서 걸려 조회되지 않는다
        when(travelPlanInvitationMapper.findActiveByTokenHash(
                TravelPlanInviteToken.hash(oldToken), "ACTIVE")).thenReturn(null);
        when(travelPlanInvitationMapper.findActiveByTokenHash(
                TravelPlanInviteToken.hash(newToken), "ACTIVE")).thenReturn(invitation());
        givenPreviewLookups();

        assertThat(travelPlanInvitationService.resolvePreview(null, oldToken)).isEmpty();
        assertThat(travelPlanInvitationService.resolvePreview(null, newToken)).isPresent();
    }

    // ── 비활성화 ────────────────────────────────────────────

    @Test
    void disablingTurnsTheActiveLinkOff() {
        givenActiveOwner();

        travelPlanInvitationService.disableInvitation(OWNER_USER_ID, PLAN_ID);

        verify(travelPlanInvitationMapper).invalidateActiveInvitation(
                PLAN_ID, "ACTIVE", "DISABLED");
        // 끄기만 하고 새 링크를 만들지 않는다
        verify(travelPlanInvitationMapper, never()).insertInvitation(any());
    }

    @Test
    void disablingTwiceIsAQuietNoOp() {
        givenActiveOwner();
        // 끌 링크가 없으면 영향 행이 0 이다
        when(travelPlanInvitationMapper.invalidateActiveInvitation(PLAN_ID, "ACTIVE", "DISABLED"))
                .thenReturn(0);

        travelPlanInvitationService.disableInvitation(OWNER_USER_ID, PLAN_ID);
        travelPlanInvitationService.disableInvitation(OWNER_USER_ID, PLAN_ID);

        // 이미 원하는 상태이므로 오류로 만들지 않는다
        verify(travelPlanInvitationMapper, atLeastOnce())
                .invalidateActiveInvitation(PLAN_ID, "ACTIVE", "DISABLED");
    }

    // ── 플래너 상태 표시 ────────────────────────────────────

    @Test
    void theOwnerScreenOnlyLearnsWhetherALinkIsOn() {
        // 화면 표시용 조회라 방 상태까지 다시 읽지 않는다
        givenOwnerMembership();
        when(travelPlanInvitationMapper.findActiveByPlanId(PLAN_ID, "ACTIVE"))
                .thenReturn(invitation());

        assertThat(travelPlanInvitationService.hasActiveInvitation(OWNER_USER_ID, PLAN_ID)).isTrue();
    }

    @Test
    void aMemberOrAnonymousNeverSeesAnInviteState() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThat(travelPlanInvitationService.hasActiveInvitation(MEMBER_USER_ID, PLAN_ID))
                .isFalse();
        assertThat(travelPlanInvitationService.hasActiveInvitation(null, PLAN_ID)).isFalse();

        verify(travelPlanInvitationMapper, never()).findActiveByPlanId(anyLong(), anyString());
    }

    // ── 미리보기 ────────────────────────────────────────────

    @Test
    void aLiveLinkShowsTheRoomWithoutAnyPersonalData() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();

        TravelPlanInvitePreviewDto preview = travelPlanInvitationService
                .resolvePreview(null, "raw-token").orElseThrow();

        assertThat(preview.getTravelPlanId()).isEqualTo(PLAN_ID);
        assertThat(preview.getTitle()).isEqualTo("제주도 여행");
        assertThat(preview.getStartDate()).isEqualTo(START);
        assertThat(preview.getEndDate()).isEqualTo(END);
        assertThat(preview.getMemberCount()).isEqualTo(3);
        // 방 표시 이름만 나가고 users 는 읽지 않는다
        assertThat(preview.getOwnerDisplayName()).isEqualTo("민준");
        assertThat(preview.isAlreadyMember()).isFalse();
        assertThat(preview.toString())
                .doesNotContain("@")
                .doesNotContain("username")
                .doesNotContain("nickname");
    }

    @Test
    void anAlreadyActiveMemberIsFlaggedSoTheScreenCanSendThemStraightIn() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(member(TravelPlanMemberStatus.ACTIVE));

        TravelPlanInvitePreviewDto preview = travelPlanInvitationService
                .resolvePreview(MEMBER_USER_ID, "raw-token").orElseThrow();
        assertThat(preview.isAlreadyMember()).isTrue();
        assertThat(preview.isJoinBlocked()).isFalse();
    }

    @Test
    void someoneWhoCannotComeBackSeesTheBlockedStateInsteadOfAJoinForm() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();

        // 내보내진 사람, 그리고 재참여가 막힌 채 나간 사람
        TravelPlanMember removed = member(TravelPlanMemberStatus.REMOVED);
        removed.setRejoinAllowed(false);
        TravelPlanMember leftWithoutRejoin = member(TravelPlanMemberStatus.LEFT);
        leftWithoutRejoin.setRejoinAllowed(false);

        for (TravelPlanMember blocked : new TravelPlanMember[]{removed, leftWithoutRejoin}) {
            when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                    .thenReturn(blocked);

            TravelPlanInvitePreviewDto preview = travelPlanInvitationService
                    .resolvePreview(MEMBER_USER_ID, "raw-token").orElseThrow();
            assertThat(preview.isJoinBlocked()).as("status=%s", blocked.getStatus()).isTrue();
            assertThat(preview.isRejoinAvailable()).isFalse();
            assertThat(preview.getRejoinDisplayName()).isNull();
            assertThat(preview.isAlreadyMember()).isFalse();
        }
    }

    @Test
    void thePreviewCarriesTheRoomLimitSoTheScreenAndTheServerAgree() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();

        TravelPlanInvitePreviewDto preview = travelPlanInvitationService
                .resolvePreview(null, "raw-token").orElseThrow();

        assertThat(preview.getMemberLimit()).isEqualTo(TravelPlanInvitationService.MAX_MEMBERS);
        assertThat(preview.isFull()).isFalse();
    }

    @Test
    void aFullRoomIsMarkedFullInThePreview() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();
        when(travelPlanMapper.countMembersByPlanAndStatus(PLAN_ID, "ACTIVE")).thenReturn(8);

        assertThat(travelPlanInvitationService.resolvePreview(null, "raw-token")
                .orElseThrow().isFull()).isTrue();
    }

    @Test
    void aReplacedOrDisabledOrUnknownTokenAllLookTheSame() {
        // 상태 조건이 걸려 있어 REPLACED / DISABLED / 없는 해시가 모두 null 로 온다
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(null);

        for (String token : new String[]{"replaced-token", "disabled-token", "unknown-token"}) {
            assertThat(travelPlanInvitationService.resolvePreview(null, token))
                    .as("token=%s", token).isEmpty();
        }
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void aLinkToARoomThatIsNoLongerActiveIsTreatedAsInvalid() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThat(travelPlanInvitationService.resolvePreview(null, "raw-token")).isEmpty();

        verify(travelPlanMapper, never()).countMembersByPlanAndStatus(anyLong(), anyString());
    }

    @Test
    void aMalformedOrEmptyTokenIsInvalidRatherThanAnError() {
        for (String malformed : new String[]{null, "", "   "}) {
            assertThat(travelPlanInvitationService.resolvePreview(null, malformed))
                    .as("token=%s", malformed).isEmpty();
        }
        // 빈 값은 조회까지 가지도 않는다
        verify(travelPlanInvitationMapper, never()).findActiveByTokenHash(anyString(), anyString());

        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(null);
        Optional<TravelPlanInvitePreviewDto> preview =
                travelPlanInvitationService.resolvePreview(null, "../../etc/passwd");
        assertThat(preview).isEmpty();
    }

    @Test
    void anInvitationFromAnotherRoomOnlyEverShowsThatRoom() {
        TravelPlanInvitation other = invitation();
        other.setTravelPlanId(OTHER_PLAN_ID);
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(other);
        when(travelPlanMapper.findPlanByIdAndStatus(OTHER_PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThat(travelPlanInvitationService.resolvePreview(null, "raw-token")).isEmpty();

        // 토큰이 가리키는 방만 본다. 다른 방으로 넘어가지 않는다
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(PLAN_ID, "ACTIVE");
    }

    // ── 참여 ────────────────────────────────────────────────

    @Test
    void aNewUserJoinsAsAnActiveMemberNeverAsAnOwner() {
        givenJoinableRoom(1);
        when(travelPlanMapper.insertMember(any())).thenReturn(1);

        Long travelPlanId = travelPlanInvitationService.join(
                MEMBER_USER_ID, "raw-token", "  예진  ");

        assertThat(travelPlanId).isEqualTo(PLAN_ID);
        TravelPlanMember saved = captureMemberInsert();
        assertThat(saved.getTravelPlanId()).isEqualTo(PLAN_ID);
        // 사용자는 로그인 정보에서 온다
        assertThat(saved.getUserId()).isEqualTo(MEMBER_USER_ID);
        assertThat(saved.getRole()).isEqualTo(TravelPlanRole.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(TravelPlanMemberStatus.ACTIVE);
        // 양끝 공백만 정리해 저장한다
        assertThat(saved.getDisplayName()).isEqualTo("예진");
        // 참여도 방의 활동이다
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
        // raw token 은 어디에도 저장되지 않는다
        assertThat(saved.toString()).doesNotContain("raw-token");
    }

    @Test
    void theCapacityIsCountedInsideTheLockOnThatRoomsRow() {
        givenJoinableRoom(1);
        when(travelPlanMapper.insertMember(any())).thenReturn(1);

        travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진");

        // 방 row 를 잠근 뒤에 인원을 세고 넣어야 동시 참여가 8명을 넘길 수 없다
        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).countMembersByPlanAndStatus(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).insertMember(any());
        // 잠금 없는 조회로 정원을 판단하지 않는다
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(PLAN_ID, "ACTIVE");
    }

    @Test
    void theLastSeatCanBeTakenButTheNinthPersonCannot() {
        givenJoinableRoom(7);
        when(travelPlanMapper.insertMember(any())).thenReturn(1);

        assertThat(travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isEqualTo(PLAN_ID);
        verify(travelPlanMapper).insertMember(any());
    }

    @Test
    void aFullRoomRefusesTheJoinOnTheServerToo() {
        givenJoinableRoom(8);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("참여 인원이 모두 찼어요.");

        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void onlyActiveMembersCountTowardsTheLimit() {
        // LEFT / REMOVED row 는 세지 않으므로 상태 조건을 건 count 만 쓴다
        givenJoinableRoom(7);
        when(travelPlanMapper.insertMember(any())).thenReturn(1);

        travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진");

        verify(travelPlanMapper).countMembersByPlanAndStatus(PLAN_ID, "ACTIVE");
        verify(travelPlanMapper, never()).countMembersByPlanAndStatus(PLAN_ID, "LEFT");
        verify(travelPlanMapper, never()).countMembersByPlanAndStatus(PLAN_ID, "REMOVED");
    }

    @Test
    void aBlankOrOverlongNameIsRejectedBeforeAnyoneJoins() {
        // 이름은 신규 참여에서만 받는다(재참여는 쓰던 이름을 쓴다).
        // 그래서 어느 쪽인지 가려진 뒤에 검사한다.
        givenJoinableRoom(1);

        for (String displayName : new String[]{null, "", "   ", "가".repeat(51)}) {
            assertThatThrownBy(() ->
                    travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", displayName))
                    .as("displayName=%s", displayName)
                    .isInstanceOf(TravelPlanValidationException.class)
                    .extracting("field").isEqualTo("displayName");
        }
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aNameAlreadyTakenInThatRoomIsRefusedWithAReadableMessage() {
        givenJoinableRoom(1);
        when(travelPlanMapper.countMembersByPlanAndDisplayName(PLAN_ID, "민준")).thenReturn(1);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "민준"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("이미 사용 중인 이름입니다.")
                .extracting("field").isEqualTo("displayName");

        verify(travelPlanMapper, never()).insertMember(any());
    }

    @Test
    void anExistingActiveMemberJustGoesBackToTheRoomWithoutASecondRow() {
        givenLockedRoom();
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(member(TravelPlanMemberStatus.ACTIVE));

        assertThat(travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isEqualTo(PLAN_ID);

        // 더블클릭이나 탭 두 개로 들어와도 row 가 하나 더 생기지 않는다
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void someoneWhoLeftComesBackOnTheirOldRowWithTheirOldName() {
        givenJoinableRoom(2);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);
        when(travelPlanMapper.reactivateLeftMember(
                left.getId(), PLAN_ID, MEMBER_USER_ID, "LEFT", "ACTIVE")).thenReturn(1);

        // 재참여에서는 이름을 다시 받지 않는다
        assertThat(travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null))
                .isEqualTo(PLAN_ID);

        // 새 row 를 만들지 않고 기존 row 를 되살린다 (member.id 가 유지되어야
        // 기존 일정/대안의 created_by_member_id 연결이 그대로 남는다)
        verify(travelPlanMapper).reactivateLeftMember(
                left.getId(), PLAN_ID, MEMBER_USER_ID, "LEFT", "ACTIVE");
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).countMembersByPlanAndDisplayName(anyLong(), anyString());
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void someoneWhoWasRemovedStillCannotSlipBackInThroughTheLink() {
        givenJoinableRoom(2);
        TravelPlanMember removed = member(TravelPlanMemberStatus.REMOVED);
        removed.setRejoinAllowed(false);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(removed);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("현재 이 여행에 다시 참여할 수 없습니다.");

        // 기존 row 를 두고 새 row 를 만들지도, 되살리지도 않는다
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void someoneWhoLeftButHadRejoinTakenAwayIsStillBlocked() {
        givenJoinableRoom(2);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(false);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("현재 이 여행에 다시 참여할 수 없습니다.");

        verify(travelPlanMapper, never()).reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void aRejoinThatNoLongerMatchesIsRefusedRatherThanReported500() {
        givenJoinableRoom(2);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);
        // 그 사이 OWNER 가 내보내 rejoin_allowed 가 내려갔다 -> 영향 행 0
        when(travelPlanMapper.reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("현재 이 여행에 다시 참여할 수 없습니다.");

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aReturningMemberTakesTheLastSeatButNotAOneBeyondIt() {
        givenJoinableRoom(7);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);
        when(travelPlanMapper.reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString())).thenReturn(1);

        // 7명 + 돌아오는 1명 = 8명
        assertThat(travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null))
                .isEqualTo(PLAN_ID);
        verify(travelPlanMapper).reactivateLeftMember(
                left.getId(), PLAN_ID, MEMBER_USER_ID, "LEFT", "ACTIVE");
    }

    @Test
    void aFullRoomTurnsAReturningMemberAwayToo() {
        givenJoinableRoom(8);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("참여 인원이 모두 찼어요.");

        // 정원은 잠금 안에서 신규/재참여 모두 같은 기준으로 본다
        verify(travelPlanMapper).countMembersByPlanAndStatus(PLAN_ID, "ACTIVE");
        verify(travelPlanMapper, never()).reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void aReturningMemberIsAlsoStoppedByALinkTurnedOffMeanwhile() {
        // 화면을 열 때는 살아 있었지만 잠금 뒤 재확인에서 꺼져 있다
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation())
                .thenReturn(null);
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan());

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("유효하지 않거나 만료된 초대 링크입니다.");

        verify(travelPlanMapper, never()).reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void theRejoinIsSerialisedByTheSameRoomLockAsANewJoin() {
        givenJoinableRoom(2);
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);
        when(travelPlanMapper.reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString())).thenReturn(1);

        travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", null);

        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).countMembersByPlanAndStatus(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).reactivateLeftMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
        order.verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void aReturningMemberIsOfferedTheirOldNameInThePreview() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        givenPreviewLookups();
        TravelPlanMember left = member(TravelPlanMemberStatus.LEFT);
        left.setRejoinAllowed(true);
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(left);

        TravelPlanInvitePreviewDto preview = travelPlanInvitationService
                .resolvePreview(MEMBER_USER_ID, "raw-token").orElseThrow();

        assertThat(preview.isRejoinAvailable()).isTrue();
        // 이름을 다시 받지 않고 쓰던 이름을 그대로 보여 준다
        assertThat(preview.getRejoinDisplayName()).isEqualTo("예진");
        // 나간 사람은 더 이상 "다시 참여할 수 없음" 상태가 아니다
        assertThat(preview.isJoinBlocked()).isFalse();
        assertThat(preview.isAlreadyMember()).isFalse();
    }

    @Test
    void aLinkTurnedOffWhileTheFormWasOpenDoesNotLetAnyoneIn() {
        // 화면을 열 때는 살아 있었지만 잠금 뒤 재확인에서 꺼져 있다
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation())
                .thenReturn(null);
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan());

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("유효하지 않거나 만료된 초대 링크입니다.");

        verify(travelPlanMapper, never()).insertMember(any());
    }

    @Test
    void aDeadOrMalformedTokenNeverReachesTheRoom() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(null);

        for (String token : new String[]{"replaced", "disabled", "unknown", "not.a.token"}) {
            assertThatThrownBy(() ->
                    travelPlanInvitationService.join(MEMBER_USER_ID, token, "예진"))
                    .as("token=%s", token)
                    .isInstanceOf(TravelPlanValidationException.class)
                    .hasMessageContaining("유효하지 않거나 만료된 초대 링크입니다.");
        }
        assertThatThrownBy(() -> travelPlanInvitationService.join(MEMBER_USER_ID, null, "예진"))
                .isInstanceOf(TravelPlanValidationException.class);

        verify(travelPlanMapper, never()).findPlanByIdAndStatusForUpdate(anyLong(), anyString());
    }

    @Test
    void aRoomThatEndedWhileTheFormWasOpenRefusesTheJoin() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("유효하지 않거나 만료된 초대 링크입니다.");

        verify(travelPlanMapper, never()).insertMember(any());
    }

    @Test
    void aUniqueKeyRaceIsTurnedIntoAnAnswerRatherThanA500() {
        givenJoinableRoom(1);
        when(travelPlanMapper.insertMember(any()))
                .thenThrow(new DuplicateKeyException("uk_travel_plan_members_plan_user"));
        // 같은 사람의 다른 요청이 먼저 끝났다
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID))
                .thenReturn(null)
                .thenReturn(member(TravelPlanMemberStatus.ACTIVE));

        assertThat(travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "예진"))
                .isEqualTo(PLAN_ID);
    }

    @Test
    void aNameRaceComesBackAsANameMessageRatherThanA500() {
        givenJoinableRoom(1);
        when(travelPlanMapper.insertMember(any()))
                .thenThrow(new DuplicateKeyException("uk_travel_plan_members_plan_display_name"));
        // 이 사람의 참여 기록은 여전히 없다 -> 이름이 겹친 것이다
        when(travelPlanMapper.findAnyMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID)).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanInvitationService.join(MEMBER_USER_ID, "raw-token", "민준"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("이미 사용 중인 이름입니다.");
    }

    @Test
    void aMissingLoginCannotJoin() {
        assertThatThrownBy(() -> travelPlanInvitationService.join(null, "raw-token", "예진"))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("userId");

        verify(travelPlanInvitationMapper, never()).findActiveByTokenHash(anyString(), anyString());
    }

    @Test
    void everyInvitationWriteRunsInsideATransaction() throws NoSuchMethodException {
        Method create = TravelPlanInvitationService.class.getMethod(
                "createInvitation", Long.class, Long.class);
        Method regenerate = TravelPlanInvitationService.class.getMethod(
                "regenerateInvitation", Long.class, Long.class);
        Method disable = TravelPlanInvitationService.class.getMethod(
                "disableInvitation", Long.class, Long.class);

        Method join = TravelPlanInvitationService.class.getMethod(
                "join", Long.class, String.class, String.class);

        // 재발급은 "끄기 + 새로 넣기", 참여는 "잠금 + 세기 + 넣기" 가 한 덩어리여야 한다
        for (Method method : new Method[]{create, regenerate, disable, join}) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s", method.getName()).isTrue();
        }
    }

    /** 방 row 를 잠근 상태까지만 준비한다(참여 기록 없음). */
    private void givenLockedRoom() {
        when(travelPlanInvitationMapper.findActiveByTokenHash(anyString(), eqActive()))
                .thenReturn(invitation());
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE"))
                .thenReturn(activePlan());
    }

    /** 잠금까지 마치고 현재 ACTIVE 인원이 {@code memberCount} 명인 방. */
    private void givenJoinableRoom(int memberCount) {
        givenLockedRoom();
        when(travelPlanMapper.countMembersByPlanAndStatus(PLAN_ID, "ACTIVE"))
                .thenReturn(memberCount);
    }

    private TravelPlan activePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setTitle("제주도 여행");
        plan.setStatus(TravelPlanStatus.ACTIVE);
        return plan;
    }

    private TravelPlanMember captureMemberInsert() {
        ArgumentCaptor<TravelPlanMember> captor = ArgumentCaptor.forClass(TravelPlanMember.class);
        verify(travelPlanMapper, atLeastOnce()).insertMember(captor.capture());
        return captor.getValue();
    }

    private String eqActive() {
        return org.mockito.ArgumentMatchers.eq("ACTIVE");
    }

    private TravelPlanInvitation captureInsert() {
        ArgumentCaptor<TravelPlanInvitation> captor =
                ArgumentCaptor.forClass(TravelPlanInvitation.class);
        verify(travelPlanInvitationMapper, atLeastOnce()).insertInvitation(captor.capture());
        return captor.getValue();
    }

    private TravelPlanMember member(TravelPlanMemberStatus status) {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(2L);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(MEMBER_USER_ID);
        member.setDisplayName("예진");
        member.setRole(TravelPlanRole.MEMBER);
        member.setStatus(status);
        return member;
    }

    private TravelPlanInvitation invitation() {
        TravelPlanInvitation invitation = new TravelPlanInvitation();
        invitation.setId(500L);
        invitation.setTravelPlanId(PLAN_ID);
        invitation.setCreatedByUserId(OWNER_USER_ID);
        invitation.setTokenHash("a".repeat(64));
        invitation.setStatus(TravelPlanInvitationStatus.ACTIVE);
        return invitation;
    }

    /** 미리보기가 방·OWNER·참여자 수를 읽는 세 조회. */
    private void givenPreviewLookups() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setTitle("제주도 여행");
        plan.setStartDate(START);
        plan.setEndDate(END);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);

        TravelPlanMember owner = new TravelPlanMember();
        owner.setId(1L);
        owner.setTravelPlanId(PLAN_ID);
        owner.setUserId(OWNER_USER_ID);
        owner.setDisplayName("민준");
        owner.setRole(TravelPlanRole.OWNER);
        owner.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndRole(PLAN_ID, "OWNER", "ACTIVE"))
                .thenReturn(owner);

        when(travelPlanMapper.countMembersByPlanAndStatus(PLAN_ID, "ACTIVE")).thenReturn(3);
    }

    private void givenActiveOwner() {
        givenOwnerMembership();
        givenActivePlan();
    }

    private void givenOwnerMembership() {
        TravelPlanMember owner = new TravelPlanMember();
        owner.setId(1L);
        owner.setTravelPlanId(PLAN_ID);
        owner.setUserId(OWNER_USER_ID);
        owner.setDisplayName("민준");
        owner.setRole(TravelPlanRole.OWNER);
        owner.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, OWNER_USER_ID, "ACTIVE"))
                .thenReturn(owner);
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenNoActiveInvitation() {
        when(travelPlanInvitationMapper.findActiveByPlanId(PLAN_ID, "ACTIVE")).thenReturn(null);
    }
}
