package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 참여자 상태 변경.
 * 어느 쪽도 row 를 지우지 않고 status 만 바꾼다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanMemberServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long OWNER_USER_ID = 7L;
    private static final Long MEMBER_USER_ID = 8L;
    private static final Long OWNER_MEMBER_ID = 11L;
    private static final Long MEMBER_A_ID = 12L;
    private static final Long MEMBER_B_ID = 13L;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @InjectMocks
    private TravelPlanMemberService travelPlanMemberService;

    // ── 스스로 나가기 ────────────────────────────────────────

    @Test
    void aMemberLeavingOnlyChangesTheStatusOfTheirOwnRow() {
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.markMemberLeft(MEMBER_A_ID, PLAN_ID, "ACTIVE", "LEFT", "MEMBER"))
                .thenReturn(1);

        travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID);

        // 상태 변경과 활동 시각 갱신이 한 덩어리다
        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper)
                .markMemberLeft(MEMBER_A_ID, PLAN_ID, "ACTIVE", "LEFT", "MEMBER");
        order.verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void leavingNeverDeletesTheMemberRowOrRewritesAuthorship() {
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.markMemberLeft(anyLong(), anyLong(), anyString(), anyString(),
                anyString())).thenReturn(1);

        travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID);

        // 과거 참여 기록과 일정/대안의 created_by_member_id 가 남아야 한다
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper).markMemberLeft(MEMBER_A_ID, PLAN_ID, "ACTIVE", "LEFT", "MEMBER");
    }

    @Test
    void theOwnerCannotSimplyWalkOut() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() -> travelPlanMemberService.leave(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("방장은 바로 나갈 수 없습니다.")
                .extracting("field").isEqualTo("role");

        // 방장이 실수로 LEFT 가 되면 안 된다
        verify(travelPlanMapper, never()).markMemberLeft(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void leavingTwiceDoesNotChangeAnythingASecondTime() {
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        // 조건부 UPDATE 라 이미 나갔으면 영향 행이 0 이다
        when(travelPlanMapper.markMemberLeft(anyLong(), anyLong(), anyString(), anyString(),
                anyString())).thenReturn(0);

        assertThatThrownBy(() -> travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void someoneWhoIsNotAnActiveMemberCannotLeave() {
        givenActivePlan();
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).markMemberLeft(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    // ── 내보내기 ────────────────────────────────────────────

    @Test
    void theOwnerCanRemoveAPlainMember() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_B_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.markMemberRemoved(
                MEMBER_B_ID, PLAN_ID, "ACTIVE", "REMOVED", "MEMBER")).thenReturn(1);

        travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID);

        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper)
                .markMemberRemoved(MEMBER_B_ID, PLAN_ID, "ACTIVE", "REMOVED", "MEMBER");
        order.verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void aPlainMemberCannotRemoveAnyone() {
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(MEMBER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 권한이 없으면 대상 조회까지 가지 않는다 (존재 여부도 알리지 않는다)
        verify(travelPlanMapper, never()).findMemberByPlanAndId(anyLong(), anyLong());
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theOwnerCannotBeRemovedByAnyone() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        // 자기 자신이자 OWNER 인 대상
        givenTarget(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, OWNER_MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aTargetThatAlreadyLeftOrWasRemovedIsNotChangedAgain() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        for (TravelPlanMemberStatus status : new TravelPlanMemberStatus[]{
                TravelPlanMemberStatus.LEFT, TravelPlanMemberStatus.REMOVED}) {
            givenTarget(member(MEMBER_B_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER, status));

            assertThatThrownBy(() ->
                    travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                    .as("status=%s", status)
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aMemberIdFromAnotherRoomNeverMatches() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        // 방 조건이 걸려 있어 다른 방의 memberId 는 조회되지 않는다
        when(travelPlanMapper.findMemberByPlanAndId(PLAN_ID, MEMBER_B_ID)).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndId(OTHER_PLAN_ID, MEMBER_B_ID);
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aMissingTargetIdIsRefusedWithoutALookup() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndId(anyLong(), anyLong());
    }

    // ── 재참여 허용 ─────────────────────────────────────────

    @Test
    void allowingRejoinOnlyLiftsTheFlagAndLeavesThemRemoved() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(removedMember(MEMBER_B_ID, false));
        when(travelPlanMapper.allowMemberRejoin(MEMBER_B_ID, PLAN_ID, "REMOVED", "MEMBER"))
                .thenReturn(1);

        travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID);

        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).allowMemberRejoin(MEMBER_B_ID, PLAN_ID, "REMOVED", "MEMBER");
        order.verify(travelPlanMapper).touchLastActivity(PLAN_ID);

        // 바로 복귀시키지 않는다. 상태는 REMOVED 그대로다
        verify(travelPlanMapper, never()).reactivateMember(
                anyLong(), anyLong(), anyLong(), anyString(), anyString());
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aPlainMemberCannotHandOutRejoinPermission() {
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(MEMBER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 권한이 없으면 대상 조회까지 가지 않는다
        verify(travelPlanMapper, never()).findMemberByPlanAndId(anyLong(), anyLong());
        verify(travelPlanMapper, never()).allowMemberRejoin(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void onlyARemovedMemberCanBeLetBackIn() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        // ACTIVE 나 LEFT 는 이 기능의 대상이 아니다
        for (TravelPlanMemberStatus status : new TravelPlanMemberStatus[]{
                TravelPlanMemberStatus.ACTIVE, TravelPlanMemberStatus.LEFT}) {
            givenTarget(member(MEMBER_B_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER, status));

            assertThatThrownBy(() ->
                    travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                    .as("status=%s", status)
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(travelPlanMapper, never()).allowMemberRejoin(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void alreadyAllowedIsNotUpdatedTwice() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(removedMember(MEMBER_B_ID, true));

        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).allowMemberRejoin(
                anyLong(), anyLong(), anyString(), anyString());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aStaleAllowIsRefusedRatherThanReported500() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(removedMember(MEMBER_B_ID, false));
        // 그 사이 다른 요청이 먼저 허용했다 -> 영향 행 0
        when(travelPlanMapper.allowMemberRejoin(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(0);

        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aMemberIdFromAnotherRoomCannotBeLetBackIn() {
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.findMemberByPlanAndId(PLAN_ID, MEMBER_B_ID)).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndId(OTHER_PLAN_ID, MEMBER_B_ID);
        verify(travelPlanMapper, never()).allowMemberRejoin(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void theFormerOwnerCannotHandOutRejoinPermissionAfterTheHandover() {
        // 허용 권한은 언제나 현재 ACTIVE OWNER 의 것이다
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.allowRejoin(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).allowMemberRejoin(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void removingSomeoneAgainAlwaysShutsTheDoorBehindThem() {
        // 한 번 허용해 준 적이 있어도 다시 내보내면 rejoin_allowed 가 다시 내려간다.
        // markMemberRemoved 가 rejoin_allowed = 0 을 항상 쓰기 때문이다.
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_B_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.markMemberRemoved(
                MEMBER_B_ID, PLAN_ID, "ACTIVE", "REMOVED", "MEMBER")).thenReturn(1);

        travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID);

        verify(travelPlanMapper)
                .markMemberRemoved(MEMBER_B_ID, PLAN_ID, "ACTIVE", "REMOVED", "MEMBER");
    }

    @Test
    void allowingRejoinRunsInsideATransaction() throws NoSuchMethodException {
        Method allow = TravelPlanMemberService.class.getMethod(
                "allowRejoin", Long.class, Long.class, Long.class);

        assertThat(allow.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ── 방장 넘기기 ─────────────────────────────────────────

    @Test
    void handingOverSwapsTheTwoRolesAndLeavesEverythingElseAlone() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        givenRoleChange(OWNER_MEMBER_ID, "OWNER", "MEMBER", 1);
        givenRoleChange(MEMBER_A_ID, "MEMBER", "OWNER", 1);

        travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID);

        // 먼저 내려놓고 넘긴다. 중간에도 방장이 둘인 순간이 없다
        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper)
                .changeMemberRole(OWNER_MEMBER_ID, PLAN_ID, "ACTIVE", "OWNER", "MEMBER");
        order.verify(travelPlanMapper)
                .changeMemberRole(MEMBER_A_ID, PLAN_ID, "ACTIVE", "MEMBER", "OWNER");
        order.verify(travelPlanMapper).touchLastActivity(PLAN_ID);

        // row 를 만들거나 지우거나 상태를 바꾸지 않는다 (id / displayName / ACTIVE 유지)
        verify(travelPlanMapper, never()).insertMember(any());
        verify(travelPlanMapper, never()).markMemberLeft(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theHandoverIsSerialisedByLockingTheRoomRowFirst() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        givenRoleChange(OWNER_MEMBER_ID, "OWNER", "MEMBER", 1);
        givenRoleChange(MEMBER_A_ID, "MEMBER", "OWNER", 1);

        travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID);

        // 잠근 뒤에 방장 여부를 다시 확인한다. 잠금 없는 조회로 판단하지 않는다
        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).findMemberByPlanAndUser(PLAN_ID, OWNER_USER_ID, "ACTIVE");
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(PLAN_ID, "ACTIVE");
    }

    @Test
    void aStaleFirstUpdateStopsTheHandoverBeforeAnyoneIsPromoted() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        // 그 사이 다른 요청이 먼저 넘겨 이 방장은 더 이상 OWNER 가 아니다
        givenRoleChange(OWNER_MEMBER_ID, "OWNER", "MEMBER", 0);

        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 방장이 둘이 되는 상태를 만들지 않는다
        verify(travelPlanMapper, never())
                .changeMemberRole(MEMBER_A_ID, PLAN_ID, "ACTIVE", "MEMBER", "OWNER");
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aStaleSecondUpdateRollsTheWholeHandoverBack() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        givenRoleChange(OWNER_MEMBER_ID, "OWNER", "MEMBER", 1);
        // 대상이 그 사이 나갔다 -> 영향 행 0
        givenRoleChange(MEMBER_A_ID, "MEMBER", "OWNER", 0);

        // 예외로 빠져나가 트랜잭션 전체가 되돌아간다 (방장 0명으로 commit 되지 않는다)
        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aPlainMemberCannotHandOverTheRoom() {
        givenLockedPlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(MEMBER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 권한이 없으면 대상 조회까지 가지 않는다
        verify(travelPlanMapper, never()).findMemberByPlanAndId(anyLong(), anyLong());
        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theOwnerCannotHandTheRoomToThemselves() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, OWNER_MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void someoneWhoLeftOrWasRemovedCannotBecomeTheOwner() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        for (TravelPlanMemberStatus status : new TravelPlanMemberStatus[]{
                TravelPlanMemberStatus.LEFT, TravelPlanMemberStatus.REMOVED}) {
            givenTarget(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER, status));

            assertThatThrownBy(() ->
                    travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                    .as("status=%s", status)
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aMemberIdFromAnotherRoomOrNoneAtAllIsRefused() {
        givenLockedPlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        // 방 조건이 걸려 있어 다른 방의 memberId 는 조회되지 않는다
        when(travelPlanMapper.findMemberByPlanAndId(PLAN_ID, MEMBER_B_ID)).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndId(OTHER_PLAN_ID, MEMBER_B_ID);
        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aRoomThatIsNoLongerActiveCannotChangeHands() {
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndUser(
                anyLong(), anyLong(), anyString());
    }

    @Test
    void theFormerOwnerStaysInTheRoomAndCanThenLeaveLikeAnyMember() {
        // 넘긴 뒤에는 MEMBER 이므로 기존 나가기 기능이 그대로 통한다.
        // leave Service 는 손대지 않았다.
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        when(travelPlanMapper.markMemberLeft(
                OWNER_MEMBER_ID, PLAN_ID, "ACTIVE", "LEFT", "MEMBER")).thenReturn(1);

        travelPlanMemberService.leave(OWNER_USER_ID, PLAN_ID);

        verify(travelPlanMapper)
                .markMemberLeft(OWNER_MEMBER_ID, PLAN_ID, "ACTIVE", "LEFT", "MEMBER");
    }

    @Test
    void theNewOwnerIsStillBlockedFromWalkingOut() {
        // 넘겨받은 사람은 이제 OWNER 라 기존 차단이 그대로 걸린다
        givenActivePlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() -> travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("방장은 바로 나갈 수 없습니다.");
    }

    @Test
    void theFormerOwnerCanNoLongerRunOwnerOnlyActions() {
        // role 이 MEMBER 로 내려갔으므로 기존 OWNER 검증에서 막힌다
        givenLockedPlan();
        givenActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));

        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanMemberService.transferOwnership(OWNER_USER_ID, PLAN_ID, MEMBER_A_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).changeMemberRole(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void theNewOwnerCanRunOwnerOnlyActions() {
        givenLockedPlan();
        givenCurrentMember(member(MEMBER_A_ID, MEMBER_USER_ID, TravelPlanRole.OWNER,
                TravelPlanMemberStatus.ACTIVE));
        givenTarget(member(MEMBER_B_ID, 99L, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.ACTIVE));
        givenRoleChange(MEMBER_A_ID, "OWNER", "MEMBER", 1);
        givenRoleChange(MEMBER_B_ID, "MEMBER", "OWNER", 1);

        // 넘겨받은 사람이 다시 다른 사람에게 넘길 수 있다
        travelPlanMemberService.transferOwnership(MEMBER_USER_ID, PLAN_ID, MEMBER_B_ID);

        verify(travelPlanMapper)
                .changeMemberRole(MEMBER_B_ID, PLAN_ID, "ACTIVE", "MEMBER", "OWNER");
    }

    @Test
    void handingOverRunsInsideATransaction() throws NoSuchMethodException {
        Method transfer = TravelPlanMemberService.class.getMethod(
                "transferOwnership", Long.class, Long.class, Long.class);

        // 두 role UPDATE 와 활동 시각 갱신이 한 덩어리여야 한다
        assertThat(transfer.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ── 공통 ────────────────────────────────────────────────

    @Test
    void neitherActionWorksOnARoomThatIsNoLongerActive() {
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanMemberService.leave(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                travelPlanMemberService.removeMember(OWNER_USER_ID, PLAN_ID, MEMBER_B_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndUser(
                anyLong(), anyLong(), anyString());
    }

    @Test
    void aMissingLoginCannotChangeAnyone() {
        givenActivePlan();

        assertThatThrownBy(() -> travelPlanMemberService.leave(null, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("userId");
    }

    @Test
    void bothActionsRunInsideATransaction() throws NoSuchMethodException {
        Method leave = TravelPlanMemberService.class.getMethod(
                "leave", Long.class, Long.class);
        Method remove = TravelPlanMemberService.class.getMethod(
                "removeMember", Long.class, Long.class, Long.class);

        // 상태 변경과 last_activity_at 갱신이 함께 반영되어야 한다
        for (Method method : new Method[]{leave, remove}) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s", method.getName()).isTrue();
        }
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private TravelPlanMember member(Long id, Long userId, TravelPlanRole role,
                                    TravelPlanMemberStatus status) {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(id);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(userId);
        member.setDisplayName("쭈니");
        member.setRole(role);
        member.setStatus(status);
        return member;
    }

    /** 내보내진 기록. rejoinAllowed 로 아직 막혀 있는지 이미 풀렸는지를 나눈다. */
    private TravelPlanMember removedMember(Long id, boolean rejoinAllowed) {
        TravelPlanMember member = member(id, MEMBER_USER_ID, TravelPlanRole.MEMBER,
                TravelPlanMemberStatus.REMOVED);
        member.setRejoinAllowed(rejoinAllowed);
        return member;
    }

    private void givenCurrentMember(TravelPlanMember member) {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, member.getUserId(), "ACTIVE"))
                .thenReturn(member);
    }

    private void givenTarget(TravelPlanMember target) {
        when(travelPlanMapper.findMemberByPlanAndId(PLAN_ID, target.getId())).thenReturn(target);
    }

    /** 방장 이전은 방 row 를 잠그고 시작한다. */
    private void givenLockedPlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenRoleChange(Long memberId, String fromRole, String toRole, int affected) {
        when(travelPlanMapper.changeMemberRole(memberId, PLAN_ID, "ACTIVE", fromRole, toRole))
                .thenReturn(affected);
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }
}
