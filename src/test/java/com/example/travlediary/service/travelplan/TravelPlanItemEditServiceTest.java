package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanItem;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanMemberStatus;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.TravelPlanStatus;
import com.example.travlediary.repository.travelplan.TravelPlanItemMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 일정 수정/삭제. 방의 ACTIVE 멤버면 자기가 쓰지 않은 일정도 다룰 수 있다(공동 편집).
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanItemEditServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final Long DAY_ID = 100L;
    private static final Long OTHER_DAY_ID = 200L;
    private static final Long ITEM_ID = 500L;
    private static final Long MEMBER_ID = 11L;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @InjectMocks
    private TravelPlanService travelPlanService;

    @Test
    void anActiveMemberCanEditAnItemSomeoneElseWrote() {
        givenEditableItem(999L);   // 다른 멤버가 작성한 일정
        when(travelPlanItemMapper.updateContent(ITEM_ID, DAY_ID, "고친 일정", 3)).thenReturn(1);

        travelPlanService.updateItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", 3);

        verify(travelPlanItemMapper).updateContent(ITEM_ID, DAY_ID, "고친 일정", 3);
        // 실제 변경이 있었으니 목록 정렬 기준을 갱신한다
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void editedContentIsTrimmedAndKeepsInnerLineBreaks() {
        givenEditableItem(MEMBER_ID);
        when(travelPlanItemMapper.updateContent(anyLong(), anyLong(), anyString(), anyInt()))
                .thenReturn(1);

        travelPlanService.updateItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID,
                "  오전 10시 경복궁\n한복 빌리기  ", 3);

        verify(travelPlanItemMapper).updateContent(
                ITEM_ID, DAY_ID, "오전 10시 경복궁\n한복 빌리기", 3);
    }

    @Test
    void aStaleVersionIsReportedAsAConflictInsteadOfOverwriting() {
        givenEditableItem(MEMBER_ID);
        // 그 사이 다른 사람이 고쳐 영향 행이 0 이다
        when(travelPlanItemMapper.updateContent(ITEM_ID, DAY_ID, "고친 일정", 3)).thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", 3))
                .isInstanceOf(TravelPlanConflictException.class)
                .hasMessageContaining("다른 변경이 먼저 반영");

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aMissingVersionIsTreatedAsAConflict() {
        givenEditableItem(MEMBER_ID);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", null))
                .isInstanceOf(TravelPlanConflictException.class);

        verify(travelPlanItemMapper, never()).updateContent(anyLong(), anyLong(), any(), any());
    }

    @Test
    void blankContentIsRejectedWithoutUpdating() {
        givenEditableItem(MEMBER_ID);

        for (String content : new String[]{null, "", "   ", "\n\n"}) {
            assertThatThrownBy(() -> travelPlanService.updateItem(
                    USER_ID, PLAN_ID, DAY_ID, ITEM_ID, content, 3))
                    .as("content=%s", content)
                    .isInstanceOf(TravelPlanValidationException.class)
                    .extracting("field").isEqualTo("content");
        }
        verify(travelPlanItemMapper, never()).updateContent(anyLong(), anyLong(), any(), any());
    }

    @Test
    void anItemFromAnotherDayCannotBeEditedOrDeleted() {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        // 소속 조건이 걸려 다른 DAY 의 itemId 는 조회되지 않는다
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", 3))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).updateContent(anyLong(), anyLong(), any(), any());
        verify(travelPlanItemMapper, never()).deleteByIdAndDayId(anyLong(), anyLong());
    }

    @Test
    void someoneElsesRoomCannotBeEditedOrDeleted() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", 3))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).findByIdAndDayId(anyLong(), anyLong());
    }

    @Test
    void aRoomThatIsNoLongerActiveCannotBeEditedOrDeleted() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "고친 일정", 3))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).findByIdAndDayId(anyLong(), anyLong());
    }

    @Test
    void deletingResequencesOnlyThatDay() {
        givenEditableItem(999L);   // 작성자가 아니어도 지울 수 있다
        when(travelPlanItemMapper.deleteByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(1);

        travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        // 삭제 뒤 그 DAY 만 1..N 으로 다시 매긴다
        InOrder order = inOrder(travelPlanItemMapper);
        order.verify(travelPlanItemMapper).deleteByIdAndDayId(ITEM_ID, DAY_ID);
        order.verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(OTHER_DAY_ID);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void anItemThatVanishedIsNotTreatedAsDeleted() {
        givenEditableItem(MEMBER_ID);
        when(travelPlanItemMapper.deleteByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(anyLong());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void addingAnItemAlsoBumpsTheRoomsLastActivity() {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "새 일정");

        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void editAndDeleteEachRunInsideATransaction() throws NoSuchMethodException {
        Method update = TravelPlanService.class.getMethod("updateItem",
                Long.class, Long.class, Long.class, Long.class, String.class, Integer.class);
        Method delete = TravelPlanService.class.getMethod("deleteItem",
                Long.class, Long.class, Long.class, Long.class);

        assertThat(update.isAnnotationPresent(Transactional.class)).isTrue();
        // 삭제와 순서 재정렬이 한 트랜잭션이어야 한다
        assertThat(delete.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private void givenEditableItem(Long authorMemberId) {
        givenActiveMembership();
        givenActivePlan();
        givenDay(DAY_ID);
        TravelPlanItem item = new TravelPlanItem();
        item.setId(ITEM_ID);
        item.setTravelPlanDayId(DAY_ID);
        item.setContent("원래 일정");
        item.setDisplayOrder(2);
        item.setCreatedByMemberId(authorMemberId);
        item.setVersion(3);
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(item);
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
        day.setDayNumber(1);
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, dayId)).thenReturn(day);
    }
}
