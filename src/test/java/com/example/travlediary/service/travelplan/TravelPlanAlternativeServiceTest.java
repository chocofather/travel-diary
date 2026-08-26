package com.example.travlediary.service.travelplan;

import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanItem;
import com.example.travlediary.model.TravelPlanItemAlternative;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A 일정에 붙는 대안(B/C).
 * 첫 대안이 B(1번), 두 번째가 C(2번)이고 세 번째는 받지 않는다.
 * A 일정과 마찬가지로 방의 ACTIVE 멤버면 자기가 쓰지 않은 대안도 다룰 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanAlternativeServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final Long DAY_ID = 100L;
    private static final Long ITEM_ID = 500L;
    private static final Long MEMBER_ID = 11L;
    private static final Long ALTERNATIVE_ID = 900L;
    private static final Long OTHER_ALTERNATIVE_ID = 901L;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private TravelPlanAlternativeMapper travelPlanAlternativeMapper;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private TravelPlanService travelPlanService;

    // ── 추가 ────────────────────────────────────────────────

    @Test
    void theFirstAlternativeBecomesB() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "비가 많이 올 때", "아쿠아플라넷 방문");

        TravelPlanItemAlternative saved = captureInsert();
        assertThat(saved.getTravelPlanItemId()).isEqualTo(ITEM_ID);
        assertThat(saved.getAlternativeOrder()).isEqualTo(1);
        assertThat(saved.getConditionLabel()).isEqualTo("비가 많이 올 때");
        assertThat(saved.getContent()).isEqualTo("아쿠아플라넷 방문");
        // 태그 UI 는 아직 없다
        assertThat(saved.getTag()).isNull();
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void theSecondAlternativeBecomesC() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(1);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        travelPlanService.addAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어");

        assertThat(captureInsert().getAlternativeOrder()).isEqualTo(2);
    }

    @Test
    void aThirdAlternativeIsRejectedByTheServerNotJustTheScreen() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(2);

        assertThatThrownBy(() -> travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "세 번째"))
                .isInstanceOf(TravelPlanValidationException.class)
                .hasMessageContaining("2개까지");

        verify(travelPlanAlternativeMapper, never()).insertAlternative(any());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void blankContentIsRejectedWithoutSaving() {
        givenItem();

        for (String content : new String[]{null, "", "   ", "\n\n"}) {
            assertThatThrownBy(() -> travelPlanService.addAlternative(
                    USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "비 올 때", content))
                    .as("content=%s", content)
                    .isInstanceOf(TravelPlanValidationException.class)
                    .extracting("field").isEqualTo("content");
        }
        verify(travelPlanAlternativeMapper, never()).insertAlternative(any());
    }

    @Test
    void contentIsTrimmedAndKeepsItsInnerLineBreaks() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        travelPlanService.addAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID,
                "  비가 올 때  ", "  실내 코스\n아쿠아플라넷  ");

        TravelPlanItemAlternative saved = captureInsert();
        assertThat(saved.getConditionLabel()).isEqualTo("비가 올 때");
        assertThat(saved.getContent()).isEqualTo("실내 코스\n아쿠아플라넷");
    }

    @Test
    void anEmptyConditionIsStoredAsNull() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        for (String condition : new String[]{null, "", "   "}) {
            travelPlanService.addAlternative(
                    USER_ID, PLAN_ID, DAY_ID, ITEM_ID, condition, "카페 투어");
            assertThat(captureInsert().getConditionLabel()).as("condition=%s", condition).isNull();
        }
    }

    @Test
    void aConditionLongerThanTheColumnIsRejected() {
        givenItem();

        assertThatThrownBy(() -> travelPlanService.addAlternative(USER_ID, PLAN_ID, DAY_ID,
                ITEM_ID, "가".repeat(101), "카페 투어"))
                .isInstanceOf(TravelPlanValidationException.class)
                .extracting("field").isEqualTo("conditionLabel");

        // 딱 100 자는 통과한다
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);
        travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "가".repeat(100), "카페 투어");
        assertThat(captureInsert().getConditionLabel()).hasSize(100);
    }

    @Test
    void theAuthorComesFromTheCurrentMembershipNotTheRequest() {
        // A 를 다른 사람이 썼어도 대안 작성자는 지금 쓰는 사람이다
        givenItem(999L);
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        travelPlanService.addAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어");

        assertThat(captureInsert().getCreatedByMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void someoneElsesRoomCannotGainAlternatives() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID,
                ITEM_ID, ALTERNATIVE_ID, null, "카페 투어", 1))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.deleteAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanAlternativeMapper, never()).insertAlternative(any());
        verify(travelPlanAlternativeMapper, never()).findByIdAndItemId(anyLong(), anyLong());
    }

    @Test
    void anItemFromAnotherDayCannotGainAlternatives() {
        givenActiveMembership();
        givenActivePlan();
        givenDay();
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어"))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanAlternativeMapper, never()).insertAlternative(any());
    }

    // ── 수정 ────────────────────────────────────────────────

    @Test
    void anActiveMemberCanEditAnAlternativeSomeoneElseWrote() {
        givenItem();
        givenAlternative(1, 999L);
        when(travelPlanAlternativeMapper.updateWithVersion(
                ALTERNATIVE_ID, ITEM_ID, "눈 올 때", "실내 박물관", 4)).thenReturn(1);

        travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID,
                "  눈 올 때  ", "  실내 박물관  ", 4);

        verify(travelPlanAlternativeMapper).updateWithVersion(
                ALTERNATIVE_ID, ITEM_ID, "눈 올 때", "실내 박물관", 4);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void aStaleVersionIsReportedAsAConflictInsteadOfOverwriting() {
        givenItem();
        givenAlternative(1, MEMBER_ID);
        // 그 사이 다른 사람이 고쳐 영향 행이 0 이다
        when(travelPlanAlternativeMapper.updateWithVersion(
                anyLong(), anyLong(), any(), anyString(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID,
                ITEM_ID, ALTERNATIVE_ID, null, "실내 박물관", 4))
                .isInstanceOf(TravelPlanConflictException.class)
                .hasMessageContaining("다른 변경이 먼저 반영");

        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    @Test
    void aMissingVersionIsTreatedAsAConflict() {
        givenItem();
        givenAlternative(1, MEMBER_ID);

        assertThatThrownBy(() -> travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID,
                ITEM_ID, ALTERNATIVE_ID, null, "실내 박물관", null))
                .isInstanceOf(TravelPlanConflictException.class);

        verify(travelPlanAlternativeMapper, never()).updateWithVersion(
                anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void anAlternativeFromAnotherItemCannotBeEditedOrDeleted() {
        givenItem();
        // 소속 조건이 걸려 다른 일정의 alternativeId 는 조회되지 않는다
        when(travelPlanAlternativeMapper.findByIdAndItemId(OTHER_ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID,
                ITEM_ID, OTHER_ALTERNATIVE_ID, null, "실내 박물관", 4))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.deleteAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, OTHER_ALTERNATIVE_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanAlternativeMapper, never()).updateWithVersion(
                anyLong(), anyLong(), any(), any(), any());
        verify(travelPlanAlternativeMapper, never()).deleteByIdAndItemId(anyLong(), anyLong());
    }

    // ── 삭제 ────────────────────────────────────────────────

    @Test
    void deletingCJustRemovesIt() {
        givenItem();
        givenAlternative(2, MEMBER_ID);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);

        travelPlanService.deleteAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID);

        // 남은 B 의 자리는 그대로다
        verify(travelPlanAlternativeMapper, never()).updateOrderByIdAndItemId(
                anyLong(), anyLong(), anyInt());
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void deletingBWithoutACJustRemovesIt() {
        givenItem();
        givenAlternative(1, MEMBER_ID);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 2)).thenReturn(null);

        travelPlanService.deleteAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID);

        verify(travelPlanAlternativeMapper, never()).updateOrderByIdAndItemId(
                anyLong(), anyLong(), anyInt());
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void deletingBPullsCUpIntoTheBSlot() {
        givenItem();
        givenAlternative(1, MEMBER_ID);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 2))
                .thenReturn(alternative(OTHER_ALTERNATIVE_ID, 2, "카페 투어", 999L));

        travelPlanService.deleteAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID);

        // 내용/조건/작성자는 그대로 두고 자리만 2 -> 1
        verify(travelPlanAlternativeMapper).updateOrderByIdAndItemId(
                OTHER_ALTERNATIVE_ID, ITEM_ID, 1);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    // ── A 삭제와 승격 ────────────────────────────────────────

    @Test
    void deletingAnItemWithABPromotesThatAlternativeIntoTheItemRow() {
        givenItem(999L);
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 1))
                .thenReturn(alternative(ALTERNATIVE_ID, 1, "실내 박물관", MEMBER_ID));
        when(travelPlanItemMapper.promoteAlternativeContent(
                anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);

        travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        // parent row 는 살아 있고 내용만 갈아 끼운다. 작성자도 B 의 작성자가 된다
        verify(travelPlanItemMapper).promoteAlternativeContent(
                ITEM_ID, DAY_ID, "실내 박물관", null, MEMBER_ID);
        verify(travelPlanItemMapper, never()).deleteByIdAndDayId(anyLong(), anyLong());
        // 자리(display_order)가 그대로라 다시 매길 것이 없다
        verify(travelPlanItemMapper, never()).resequenceDisplayOrder(anyLong());
        verify(travelPlanAlternativeMapper).deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void deletingAnItemWithBAndCMovesBUpToAAndCUpToB() {
        givenItem();
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 1))
                .thenReturn(alternative(ALTERNATIVE_ID, 1, "실내 박물관", 999L));
        when(travelPlanItemMapper.promoteAlternativeContent(
                anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 2))
                .thenReturn(alternative(OTHER_ALTERNATIVE_ID, 2, "카페 투어", 999L));

        travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        InOrder order = inOrder(travelPlanItemMapper, travelPlanAlternativeMapper);
        order.verify(travelPlanItemMapper).promoteAlternativeContent(
                ITEM_ID, DAY_ID, "실내 박물관", null, 999L);
        order.verify(travelPlanAlternativeMapper).deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID);
        order.verify(travelPlanAlternativeMapper).updateOrderByIdAndItemId(
                OTHER_ALTERNATIVE_ID, ITEM_ID, 1);
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void anItemThatVanishedDuringPromotionIsNotTreatedAsPromoted() {
        givenItem();
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 1))
                .thenReturn(alternative(ALTERNATIVE_ID, 1, "실내 박물관", MEMBER_ID));
        when(travelPlanItemMapper.promoteAlternativeContent(
                anyLong(), anyLong(), anyString(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanAlternativeMapper, never()).deleteByIdAndItemId(anyLong(), anyLong());
        verify(travelPlanMapper, never()).touchLastActivity(anyLong());
    }

    // ── 전체 삭제 ───────────────────────────────────────────

    @Test
    void theGroupDeleteRemovesTheItemAndLetsTheAlternativesCascade() {
        givenItem();
        when(travelPlanItemMapper.deleteByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(1);

        travelPlanService.deleteItemGroup(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        InOrder order = inOrder(travelPlanItemMapper);
        order.verify(travelPlanItemMapper).deleteByIdAndDayId(ITEM_ID, DAY_ID);
        // 줄이 하나 빠졌으니 그 DAY 만 1..N 으로 다시 매긴다
        order.verify(travelPlanItemMapper).resequenceDisplayOrder(DAY_ID);
        // 대안은 FK CASCADE 로 함께 사라진다
        verify(travelPlanAlternativeMapper, never()).deleteByIdAndItemId(anyLong(), anyLong());
        verify(travelPlanItemMapper, never()).promoteAlternativeContent(
                anyLong(), anyLong(), anyString(), any(), any());
        verify(travelPlanMapper).touchLastActivity(PLAN_ID);
    }

    @Test
    void theGroupDeleteIsAlsoScopedToTheRoomAndTheDay() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() ->
                travelPlanService.deleteItemGroup(USER_ID, PLAN_ID, DAY_ID, ITEM_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanItemMapper, never()).deleteByIdAndDayId(anyLong(), anyLong());
    }

    @Test
    void everyAlternativeWriteRunsInsideATransaction() throws NoSuchMethodException {
        Method add = TravelPlanService.class.getMethod("addAlternative",
                Long.class, Long.class, Long.class, Long.class, String.class, String.class);
        Method update = TravelPlanService.class.getMethod("updateAlternative",
                Long.class, Long.class, Long.class, Long.class, Long.class,
                String.class, String.class, Integer.class);
        Method delete = TravelPlanService.class.getMethod("deleteAlternative",
                Long.class, Long.class, Long.class, Long.class, Long.class);
        Method deleteGroup = TravelPlanService.class.getMethod("deleteItemGroup",
                Long.class, Long.class, Long.class, Long.class);

        // 승격은 여러 문장이 한 덩어리로 반영되어야 한다
        for (Method method : new Method[]{add, update, delete, deleteGroup}) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s", method.getName()).isTrue();
        }
    }

    private TravelPlanItemAlternative captureInsert() {
        ArgumentCaptor<TravelPlanItemAlternative> captor =
                ArgumentCaptor.forClass(TravelPlanItemAlternative.class);
        verify(travelPlanAlternativeMapper, org.mockito.Mockito.atLeastOnce())
                .insertAlternative(captor.capture());
        return captor.getValue();
    }

    private TravelPlanItemAlternative alternative(Long id, int order, String content,
                                                  Long authorMemberId) {
        TravelPlanItemAlternative alternative = new TravelPlanItemAlternative();
        alternative.setId(id);
        alternative.setTravelPlanItemId(ITEM_ID);
        alternative.setAlternativeOrder(order);
        // A 에는 조건 개념이 없어 승격할 때 버려진다
        alternative.setConditionLabel("비가 많이 올 때");
        alternative.setContent(content);
        alternative.setTag(null);
        alternative.setCreatedByMemberId(authorMemberId);
        alternative.setVersion(4);
        return alternative;
    }

    private void givenAlternative(int order, Long authorMemberId) {
        when(travelPlanAlternativeMapper.findByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(alternative(ALTERNATIVE_ID, order, "실내 박물관", authorMemberId));
    }

    private void givenItem() {
        givenItem(MEMBER_ID);
    }

    private void givenItem(Long authorMemberId) {
        givenActiveMembership();
        givenActivePlan();
        givenDay();
        TravelPlanItem item = new TravelPlanItem();
        item.setId(ITEM_ID);
        item.setTravelPlanDayId(DAY_ID);
        item.setContent("경복궁");
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
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(member);
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        // 일정을 고치는 길은 방 row 를 잠그고 읽는다(완료 처리와 한 줄로 서기 위해)
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private void givenDay() {
        TravelPlanDay day = new TravelPlanDay();
        day.setId(DAY_ID);
        day.setTravelPlanId(PLAN_ID);
        day.setDayNumber(1);
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, DAY_ID)).thenReturn(day);
    }
}
