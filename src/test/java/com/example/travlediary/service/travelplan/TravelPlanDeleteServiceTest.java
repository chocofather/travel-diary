package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 방장이 진행 중인 방을 통째로 지운다.
 *
 * <p>참여자를 한 명씩 내보내는 것이 아니라 방 row 하나가 사라지고,
 * 딸린 데이터는 DB 의 CASCADE 가 정리한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanDeleteServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OWNER_USER_ID = 7L;
    private static final Long MEMBER_USER_ID = 8L;
    private static final Long OWNER_MEMBER_ID = 11L;
    private static final Long MEMBER_ID = 12L;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    /** 방이 사라진 사실을 알리는 일만 맡긴다. 실제 전송은 커밋 뒤다. */
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private TravelPlanDeleteService travelPlanDeleteService;

    @Test
    void theOwnerCanCloseARoomThatIsStillRunning() {
        givenLockedActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER));
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(1);

        travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID);

        verify(travelPlanMapper).deletePlanByIdAndStatus(PLAN_ID, "ACTIVE");
    }

    @Test
    void thePlainMemberCannotCloseTheRoom() {
        givenLockedActivePlan();
        givenCurrentMember(member(MEMBER_ID, MEMBER_USER_ID, TravelPlanRole.MEMBER));

        assertThatThrownBy(() -> travelPlanDeleteService.deletePlan(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class)
                // 권한이 없는 사람에게는 그 방이 있는지조차 알리지 않는다
                .hasMessageContaining("404");

        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void someoneWhoIsNotInTheRoomCannotCloseIt() {
        givenLockedActivePlan();
        // 나갔거나 내보내진 사람도, 애초에 참여한 적 없는 사람도 여기서 걸린다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, MEMBER_USER_ID, "ACTIVE"))
                .thenReturn(null);

        assertThatThrownBy(() -> travelPlanDeleteService.deletePlan(MEMBER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void aRoomThatIsNoLongerRunningIsNotDeletedHere() {
        // 완료된 여행은 최종본으로 남고, 그것을 치우는 길은 각자의 목록에 따로 있다
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 자격을 보기 전에 끝난다. 없는 방의 참여자를 찾을 이유가 없다
        verify(travelPlanMapper, never())
                .findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
        verify(travelPlanMapper, never()).deletePlanByIdAndStatus(anyLong(), anyString());
    }

    @Test
    void theRoomRowIsLockedBeforeAnythingElseHappens() {
        givenLockedActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER));
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(1);

        travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID);

        /*
          잠금을 먼저 잡아야 같은 순간의 완료와 한 줄로 선다.
          완료가 먼저면 이 방은 ACTIVE 가 아니라 걸리고,
          이쪽이 먼저면 완료가 사라진 방을 보고 멈춘다.
        */
        InOrder order = inOrder(travelPlanMapper);
        order.verify(travelPlanMapper).findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE");
        order.verify(travelPlanMapper).findMemberByPlanAndUser(PLAN_ID, OWNER_USER_ID, "ACTIVE");
        order.verify(travelPlanMapper).deletePlanByIdAndStatus(PLAN_ID, "ACTIVE");
    }

    @Test
    void theChildRowsAreLeftToTheDatabase() {
        givenLockedActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER));
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(1);

        travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID);

        /*
          참여자를 한 명씩 정리하지 않는다.
          방 row 를 지우면 참여 기록도 CASCADE 로 함께 사라진다.
          여기서 순서를 관리하기 시작하면 테이블이 늘 때마다 빠뜨릴 자리가 생긴다.
        */
        verify(travelPlanMapper, never()).markMemberLeft(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(travelPlanMapper, never()).markMemberRemoved(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(travelPlanMapper, never()).updatePlanStatus(
                anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void theRoomIsToldOnlyAfterTheDeleteActuallyHappened() {
        givenLockedActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER));
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(1);

        travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isEqualTo(new TravelPlanDeletedEvent(PLAN_ID));
    }

    @Test
    void aDeleteThatDidNotHappenIsNotAnnounced() {
        givenLockedActivePlan();
        givenCurrentMember(member(OWNER_MEMBER_ID, OWNER_USER_ID, TravelPlanRole.OWNER));
        // 그 사이 방이 이미 사라졌거나 상태가 달라졌다
        when(travelPlanMapper.deletePlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(0);

        assertThatThrownBy(() -> travelPlanDeleteService.deletePlan(OWNER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);

        // 되돌아간 삭제를 보고 멀쩡한 방에서 사람들이 튕겨 나가면 안 된다
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aMissingLoginIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> travelPlanDeleteService.deletePlan(null, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("userId");

        verify(travelPlanMapper, never())
                .findPlanByIdAndStatusForUpdate(anyLong(), anyString());
    }

    @Test
    void deletingRunsInsideATransaction() throws NoSuchMethodException {
        // 지우기와 알림 준비가 한 덩어리여야 되돌아간 삭제가 새어 나가지 않는다
        Method method = TravelPlanDeleteService.class
                .getMethod("deletePlan", Long.class, Long.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    private void givenLockedActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenCurrentMember(TravelPlanMember member) {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, member.getUserId(), "ACTIVE"))
                .thenReturn(member);
    }

    private TravelPlanMember member(Long id, Long userId, TravelPlanRole role) {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(id);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(userId);
        member.setDisplayName("쭈니");
        member.setRole(role);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        return member;
    }
}
