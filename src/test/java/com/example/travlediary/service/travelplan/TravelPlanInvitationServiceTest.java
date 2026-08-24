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
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID, "ACTIVE"))
                .thenReturn(new TravelPlanMember());

        assertThat(travelPlanInvitationService.resolvePreview(MEMBER_USER_ID, "raw-token")
                .orElseThrow().isAlreadyMember()).isTrue();
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

    @Test
    void everyInvitationWriteRunsInsideATransaction() throws NoSuchMethodException {
        Method create = TravelPlanInvitationService.class.getMethod(
                "createInvitation", Long.class, Long.class);
        Method regenerate = TravelPlanInvitationService.class.getMethod(
                "regenerateInvitation", Long.class, Long.class);
        Method disable = TravelPlanInvitationService.class.getMethod(
                "disableInvitation", Long.class, Long.class);

        // 재발급은 "끄기 + 새로 넣기" 가 한 덩어리여야 한다
        for (Method method : new Method[]{create, regenerate, disable}) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s", method.getName()).isTrue();
        }
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
