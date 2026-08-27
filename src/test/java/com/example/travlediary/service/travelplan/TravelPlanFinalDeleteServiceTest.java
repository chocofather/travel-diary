package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.travelplan.TravelPlanFinalMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 완료된 여행 지우기.
 *
 * <p>사용자에게는 언제나 "내 목록에서 삭제" 하나지만,
 * 마지막 한 사람이 지우면 그 여행 자체가 사라진다.
 * 기준은 지운 사람 수가 아니라 <em>남은</em> 사람 수다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanFinalDeleteServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long USER_A = 7L;
    private static final Long USER_B = 8L;
    private static final Long USER_C = 9L;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanFinalMapper travelPlanFinalMapper;
    @InjectMocks
    private TravelPlanFinalDeleteService deleteService;

    // ── 아직 보관 중인 사람이 있을 때 ─────────────────────────

    @Test
    void withTwoOfUsMyDeletionOnlyClearsMyOwnCopy() {
        // A 가 지워도 B 가 남아 있다
        givenCompletedRoom();
        givenMyRowCleared(USER_A);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(1);

        assertThat(deleteService.deleteForMe(USER_A, PLAN_ID)).isFalse();

        // 최종본도 원본 방도 그대로다
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void withThreeOfUsTwoDeletionsStillLeaveTheTripStanding() {
        // A 가 지우고, 이어서 B 가 지워도 C 가 남아 있다
        givenCompletedRoom();
        givenMyRowCleared(USER_A);
        givenMyRowCleared(USER_B);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(2, 1);

        assertThat(deleteService.deleteForMe(USER_A, PLAN_ID)).isFalse();
        assertThat(deleteService.deleteForMe(USER_B, PLAN_ID)).isFalse();

        // 채팅·투표·원본 일정까지 그대로 남는다
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    // ── 마지막 한 사람일 때 ──────────────────────────────────

    @Test
    void theLastPersonToDeleteItTakesTheWholeTripWithThem() {
        givenCompletedRoom();
        givenMyRowCleared(USER_C);
        // 이제 아무도 보관하고 있지 않다
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(0);
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "COMPLETED")).thenReturn(1);

        assertThat(deleteService.deleteForMe(USER_C, PLAN_ID)).isTrue();

        verify(travelPlanMapper).deletePlanByIdAndStatus(PLAN_ID, "COMPLETED");
    }

    @Test
    void theWholeTripGoesInOneGoRatherThanTableByTable() {
        /*
          딸린 것(참여자·설정·초대·일정·대안·채팅·투표·최종본)은
          travel_plans 를 향한 ON DELETE CASCADE 로 함께 사라진다.
          여기서 순서를 잡아 지우지 않는다.
        */
        givenCompletedRoom();
        givenMyRowCleared(USER_C);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(0);
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "COMPLETED")).thenReturn(1);

        deleteService.deleteForMe(USER_C, PLAN_ID);

        assertThat(TravelPlanFinalDeleteService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("TravelPlanMapper", "TravelPlanFinalMapper");
        verify(travelPlanMapper, times(1)).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void itIsTheRemainingPeopleThatAreCountedNotTheGoneOnes() {
        // 지운 사람이 둘이어도 남은 사람이 있으면 아무것도 사라지지 않는다
        givenCompletedRoom();
        givenMyRowCleared(USER_B);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(1);

        deleteService.deleteForMe(USER_B, PLAN_ID);

        verify(travelPlanFinalMapper).countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED");
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    // ── 돌아올 수 없는 계정은 남은 사람이 아니다 ─────────────

    @Test
    void someoneWhoWithdrewDoesNotKeepTheTripAlive() {
        /*
          A 와 B 가 완료한 뒤 B 가 탈퇴했다.
          B 는 다시 들어와 지울 수 없으므로 A 가 마지막 한 사람이다.
          함께 세면 아무도 볼 수 없는 여행이 영원히 남는다.
        */
        givenCompletedRoom();
        givenMyRowCleared(USER_A);
        // 탈퇴한 B 의 행은 hidden_at 이 비어 있어도 세지 않는다
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED"))
                .thenReturn(0);
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "COMPLETED")).thenReturn(1);

        assertThat(deleteService.deleteForMe(USER_A, PLAN_ID)).isTrue();

        verify(travelPlanMapper).deletePlanByIdAndStatus(PLAN_ID, "COMPLETED");
    }

    @Test
    void itIsTheWithdrawnStatusThatIsLeftOutAndNothingElse() {
        // 어떤 상태를 빼는지는 enum 에서 가져온다. 문자열을 지어내지 않는다
        givenCompletedRoom();
        givenMyRowCleared(USER_A);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED"))
                .thenReturn(1);

        deleteService.deleteForMe(USER_A, PLAN_ID);

        verify(travelPlanFinalMapper).countVisibleMembersByPlanId(
                PLAN_ID, UserStatus.DEACTIVATED.name());
    }

    @Test
    void oneLivingAccountIsEnoughToKeepEverything() {
        /*
          탈퇴한 사람이 섞여 있어도 살아 있는 계정이 하나라도 남으면
          최종본도 원본 방도 채팅도 투표도 그대로 둔다.
        */
        givenCompletedRoom();
        givenMyRowCleared(USER_A);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED"))
                .thenReturn(1);

        assertThat(deleteService.deleteForMe(USER_A, PLAN_ID)).isFalse();

        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    // ── 동시에 마지막을 눌렀을 때 ────────────────────────────

    @Test
    void theRoomIsLockedBeforeAnyoneIsCountedOrAnythingIsRemoved() {
        /*
          세는 것과 지우는 것이 잠금 안에서 일어나야
          마지막 두 사람이 동시에 눌러도 "둘 다 아직 남아 있다" 로 보지 않는다.
        */
        givenCompletedRoom();
        givenMyRowCleared(USER_C);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(0);
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "COMPLETED")).thenReturn(1);

        deleteService.deleteForMe(USER_C, PLAN_ID);

        InOrder order = inOrder(travelPlanMapper, travelPlanFinalMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "COMPLETED");
        order.verify(travelPlanFinalMapper).hideSnapshotForUser(PLAN_ID, USER_C);
        order.verify(travelPlanFinalMapper).countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED");
        order.verify(travelPlanMapper).deletePlanByIdAndStatus(PLAN_ID, "COMPLETED");
        // 잠금 없는 조회로 판단하지 않는다
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void whoeverArrivesSecondFindsTheTripAlreadyGone() {
        // 먼저 지나간 쪽이 지우고 나면, 잠금이 풀린 뒤 볼 방이 없다
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "COMPLETED"))
                .thenReturn(null);

        assertThatThrownBy(() -> deleteService.deleteForMe(USER_B, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 두 번째 요청은 아무것도 건드리지 않는다
        verify(travelPlanFinalMapper, never()).hideSnapshotForUser(anyLong(), anyLong());
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void aDeletionThatDidNotLandRemovesNothing() {
        // 조건부 UPDATE 가 0 이면(이미 지웠거나 남의 것) 거기서 멈춘다
        givenCompletedRoom();
        when(travelPlanFinalMapper.hideSnapshotForUser(PLAN_ID, USER_A)).thenReturn(0);

        assertThatThrownBy(() -> deleteService.deleteForMe(USER_A, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 세지도, 지우지도 않는다
        verify(travelPlanFinalMapper, never())
                .countVisibleMembersByPlanId(anyLong(), anyString());
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void everythingHappensInOnePieceOrNotAtAll() throws NoSuchMethodException {
        /*
          숨기기와 지우기가 갈라지면 아무에게도 보이지 않는데
          데이터만 남는 여행이 생긴다.
        */
        Method method = TravelPlanFinalDeleteService.class.getDeclaredMethod(
                "deleteForMe", Long.class, Long.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ── 닿으면 안 되는 것들 ──────────────────────────────────

    @Test
    void aRunningTripIsNeverReachedByThisPath() {
        /*
          진행 중인 방에는 어떤 경로로도 닿지 않는다.
          잠글 때도 지울 때도 COMPLETED 조건을 함께 건다.
        */
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "COMPLETED"))
                .thenReturn(null);

        assertThatThrownBy(() -> deleteService.deleteForMe(USER_A, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never())
                .findPlanByIdAndStatusForUpdate(PLAN_ID, TravelPlanStatus.ACTIVE.name());
        verify(travelPlanMapper, never())
                .deletePlanByIdAndStatus(PLAN_ID, TravelPlanStatus.ACTIVE.name());
    }

    @Test
    void onlyTheTripThatWasAskedForIsEverTouched() {
        givenCompletedRoom();
        givenMyRowCleared(USER_C);
        when(travelPlanFinalMapper.countVisibleMembersByPlanId(PLAN_ID, "DEACTIVATED")).thenReturn(0);
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "COMPLETED")).thenReturn(1);

        deleteService.deleteForMe(USER_C, PLAN_ID);

        // 다른 여행은 세지도 지우지도 않는다
        verify(travelPlanFinalMapper, never())
                .countVisibleMembersByPlanId(eq(OTHER_PLAN_ID), anyString());
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(OTHER_PLAN_ID, "COMPLETED");
    }

    @Test
    void aMissingLoginNeverReachesTheDatabase() {
        assertThatThrownBy(() -> deleteService.deleteForMe(null, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> deleteService.deleteForMe(USER_A, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findPlanByIdAndStatusForUpdate(anyLong(), anyString());
        verify(travelPlanFinalMapper, never()).hideSnapshotForUser(anyLong(), anyLong());
    }

    // ── 준비 ────────────────────────────────────────────────

    /** 잠금까지 마친 완료된 방. */
    private void givenCompletedRoom() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.COMPLETED);
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "COMPLETED"))
                .thenReturn(plan);
    }

    /** 그 사람의 명단 행에 지운 시각이 적힌다. */
    private void givenMyRowCleared(Long userId) {
        when(travelPlanFinalMapper.hideSnapshotForUser(PLAN_ID, userId)).thenReturn(1);
    }
}
