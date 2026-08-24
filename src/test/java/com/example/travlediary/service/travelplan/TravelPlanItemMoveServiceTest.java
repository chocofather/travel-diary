package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanItem;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanAlternativeMapper;
import com.example.travlediary.repository.travelplan.TravelPlanItemMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 일정 위/아래 이동과 다른 DAY 이동.
 * 방의 ACTIVE 멤버면 남이 쓴 일정도 옮길 수 있고, version 이 어긋나면 옮기지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanItemMoveServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final Long DAY_ID = 100L;
    private static final Long TARGET_DAY_ID = 200L;
    private static final Long ITEM_ID = 500L;
    private static final Long NEIGHBOUR_ID = 501L;
    private static final Long MEMBER_ID = 11L;
    private static final int VERSION = 3;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private TravelPlanAlternativeMapper travelPlanAlternativeMapper;
    @InjectMocks
    private TravelPlanService travelPlanService;

    @Test
    void movingUpSwapsWithThePreviousItemAndKeepsTheDayContinuous() {
        givenMovableItem(2);
        givenDayEndsAt(3);
        when(travelPlanItemMapper.findPreviousItem(DAY_ID, 2)).thenReturn(neighbour(1));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 1, VERSION))
                .thenReturn(1);

        travelPlanService.moveItemUp(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);

        // 이웃을 DAY 끝 너머의 빈 자리로 비켜 둔 뒤 교환하고, 마지막에 1..N 으로 정리한다
        InOrder order = inOrder(travelPlanItemMapper);
        order.verify(travelPlanItemMapper).updateDisplayOrderById(NEIGHBOUR_ID, DAY_ID, 4);
        order.verify(travelPlanItemMapper)
                .updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 1, VERSION);
        order.verify(travelPlanItemMapper).updateDisplayOrderById(NEIGHBOUR_ID, DAY_ID, 2);
        order.verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
        // 다른 DAY 는 건드리지 않는다
        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(TARGET_DAY_ID);
    }

    @Test
    void movingDownSwapsWithTheNextItem() {
        givenMovableItem(2);
        givenDayEndsAt(3);
        when(travelPlanItemMapper.findNextItem(DAY_ID, 2)).thenReturn(neighbour(3));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 3, VERSION))
                .thenReturn(1);

        travelPlanService.moveItemDown(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);

        verify(travelPlanItemMapper).updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 3, VERSION);
        verify(travelPlanItemMapper).updateDisplayOrderById(NEIGHBOUR_ID, DAY_ID, 2);
        verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
    }

    /**
     * DB 의 chk_travel_plan_items_display_order (display_order >= 1) 회귀 테스트.
     * 교환 도중이라도 0 이나 음수를 써서는 안 된다.
     */
    @Test
    void noSwapStepEverWritesADisplayOrderBelowOne() {
        // DAY 1: A(1) B(2) C(3) 에서 A 를 아래로 내린다
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        givenItem(1);
        givenDayEndsAt(3);
        when(travelPlanItemMapper.findNextItem(DAY_ID, 1)).thenReturn(neighbour(2));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 2, VERSION))
                .thenReturn(1);

        travelPlanService.moveItemDown(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);

        ArgumentCaptor<Integer> orders = ArgumentCaptor.forClass(Integer.class);
        verify(travelPlanItemMapper, atLeastOnce())
                .updateDisplayOrderById(anyLong(), anyLong(), orders.capture());
        verify(travelPlanItemMapper, atLeastOnce())
                .updateDisplayOrderWithVersion(anyLong(), anyLong(), orders.capture(), anyInt());

        assertThat(orders.getAllValues())
                .as("교환 도중 전달된 display_order")
                .isNotEmpty()
                .allMatch(order -> order >= 1);

        // A 와 B 만 자리를 바꾸고 C 는 그대로 3 번이다
        verify(travelPlanItemMapper).updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 2, VERSION);
        verify(travelPlanItemMapper).updateDisplayOrderById(NEIGHBOUR_ID, DAY_ID, 1);
        verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
    }

    @Test
    void theFirstItemCannotMoveUpAndTheLastCannotMoveDown() {
        givenMovableItem(1);
        when(travelPlanItemMapper.findPreviousItem(DAY_ID, 1)).thenReturn(null);
        assertThatThrownBy(() -> travelPlanService.moveItemUp(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("첫 번째");

        when(travelPlanItemMapper.findNextItem(DAY_ID, 1)).thenReturn(null);
        assertThatThrownBy(() -> travelPlanService.moveItemDown(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("마지막");

        verify(travelPlanItemMapper, never())
                .updateDisplayOrderWithVersion(anyLong(), anyLong(), anyInt(), anyInt());
        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(anyLong());
    }

    @Test
    void aStaleVersionStopsTheSwap() {
        givenMovableItem(2);
        givenDayEndsAt(3);
        when(travelPlanItemMapper.findPreviousItem(DAY_ID, 2)).thenReturn(neighbour(1));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 1, VERSION))
                .thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.moveItemUp(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(TravelPlanConflictException.class);

        // 예외로 트랜잭션이 롤백되므로 뒷정리를 진행하지 않는다
        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(anyLong());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aMissingVersionIsTreatedAsAConflict() {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        givenItem(2);

        assertThatThrownBy(() -> travelPlanService.moveItemUp(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null))
                .isInstanceOf(TravelPlanConflictException.class);
        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, null))
                .isInstanceOf(TravelPlanConflictException.class);
    }

    @Test
    void movingToAnotherDayAppendsToItsEndAndResequencesBothDays() {
        givenMovableItem(2);
        givenDay(TARGET_DAY_ID);
        // 대상 DAY 에 이미 2개가 있으면 새 자리는 3번이다
        when(travelPlanItemMapper.findMaxDisplayOrder(TARGET_DAY_ID)).thenReturn(2);
        when(travelPlanItemMapper.moveToDayWithVersion(
                ITEM_ID, DAY_ID, TARGET_DAY_ID, 3, VERSION)).thenReturn(1);

        travelPlanService.moveItemToDay(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION);

        verify(travelPlanItemMapper).moveToDayWithVersion(ITEM_ID, DAY_ID, TARGET_DAY_ID, 3, VERSION);
        // 빠져나온 DAY 와 받은 DAY 모두 번호를 이어 준다
        verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
        verify(travelPlanItemMapper).resequenceDisplayOrder(TARGET_DAY_ID);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void movingIntoAnEmptyDayStartsAtOne() {
        givenMovableItem(2);
        givenDay(TARGET_DAY_ID);
        when(travelPlanItemMapper.findMaxDisplayOrder(TARGET_DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.moveToDayWithVersion(
                ITEM_ID, DAY_ID, TARGET_DAY_ID, 1, VERSION)).thenReturn(1);

        travelPlanService.moveItemToDay(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION);

        verify(travelPlanItemMapper).moveToDayWithVersion(ITEM_ID, DAY_ID, TARGET_DAY_ID, 1, VERSION);
    }

    @Test
    void movingToTheSameDayOrNowhereIsRejected() {
        givenMovableItem(2);

        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, DAY_ID, VERSION))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("targetDayId");
        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, VERSION))
                .isInstanceOf(TravelPlanValidationException.class);

        verify(travelPlanItemMapper, never())
                .moveToDayWithVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void aTargetDayFromAnotherPlanIsRejected() {
        givenMovableItem(2);
        // 방 소속 조건이 걸려 다른 방의 dayId 는 조회되지 않는다
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, TARGET_DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never())
                .moveToDayWithVersion(anyLong(), anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void aStaleVersionStopsTheDayMoveBeforeAnyResequence() {
        givenMovableItem(2);
        givenDay(TARGET_DAY_ID);
        when(travelPlanItemMapper.findMaxDisplayOrder(TARGET_DAY_ID)).thenReturn(2);
        when(travelPlanItemMapper.moveToDayWithVersion(
                ITEM_ID, DAY_ID, TARGET_DAY_ID, 3, VERSION)).thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION))
                .isInstanceOf(TravelPlanConflictException.class);

        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(anyLong());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void someoneElsesRoomOrAnInactiveRoomCannotReorder() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);
        assertThatThrownBy(() -> travelPlanService.moveItemUp(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(ResponseStatusException.class);

        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);
        assertThatThrownBy(() -> travelPlanService.moveItemDown(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).findByIdAndDayId(anyLong(), anyLong());
    }

    @Test
    void anItemFromAnotherDayCannotBeMoved() {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.moveItemUp(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void alternativesTravelWithTheirItemWithoutAnyExtraWrite() {
        // B/C 는 parent item 에 걸려 있으므로 이동은 대안 테이블을 건드릴 일이 없다.
        givenMovableItem(2);
        givenDayEndsAt(3);
        when(travelPlanItemMapper.findPreviousItem(DAY_ID, 2)).thenReturn(neighbour(1));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 1, VERSION))
                .thenReturn(1);
        when(travelPlanItemMapper.findNextItem(DAY_ID, 2)).thenReturn(neighbour(3));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 3, VERSION))
                .thenReturn(1);
        givenDay(TARGET_DAY_ID);
        when(travelPlanItemMapper.moveToDayWithVersion(
                ITEM_ID, DAY_ID, TARGET_DAY_ID, 1, VERSION)).thenReturn(1);

        travelPlanService.moveItemUp(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);
        travelPlanService.moveItemDown(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);
        travelPlanService.moveItemToDay(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION);

        // 대안에 별도 day_id 를 두지 않으므로 옮겨 다녀도 연결이 그대로다
        verifyNoInteractions(travelPlanAlternativeMapper);
    }

    @Test
    void everyMoveRunsInsideATransaction() throws NoSuchMethodException {
        Method up = TravelPlanService.class.getMethod("moveItemUp",
                Long.class, Long.class, Long.class, Long.class, Integer.class);
        Method down = TravelPlanService.class.getMethod("moveItemDown",
                Long.class, Long.class, Long.class, Long.class, Integer.class);
        Method toDay = TravelPlanService.class.getMethod("moveItemToDay",
                Long.class, Long.class, Long.class, Long.class, Long.class, Integer.class);

        assertThat(up.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(down.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(toDay.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private void givenMovableItem(int displayOrder) {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        givenItem(displayOrder);
    }

    /** 교환에 쓸 임시 자리를 DAY 끝 너머에서 잡는다. */
    private void givenDayEndsAt(int lastOrder) {
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(lastOrder);
    }

    private void givenItem(int displayOrder) {
        TravelPlanItem item = new TravelPlanItem();
        item.setId(ITEM_ID);
        item.setTravelPlanDayId(DAY_ID);
        item.setContent("옮길 일정");
        item.setDisplayOrder(displayOrder);
        // 남이 쓴 일정도 옮길 수 있다
        item.setCreatedByMemberId(999L);
        item.setVersion(VERSION);
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(item);
    }

    private TravelPlanItem neighbour(int displayOrder) {
        TravelPlanItem item = new TravelPlanItem();
        item.setId(NEIGHBOUR_ID);
        item.setTravelPlanDayId(DAY_ID);
        item.setDisplayOrder(displayOrder);
        item.setVersion(1);
        return item;
    }

    private void givenActiveMembership() {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(MEMBER_ID);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setRole(TravelPlanRole.MEMBER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(member);
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenDay(Long dayId) {
        TravelPlanDay day = new TravelPlanDay();
        day.setId(dayId);
        day.setTravelPlanId(PLAN_ID);
        day.setDayNumber(dayId.equals(DAY_ID) ? 1 : 2);
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, dayId)).thenReturn(day);
    }
}
