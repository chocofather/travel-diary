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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A 일정이 실제로 바뀌었을 때만, 그리고 사용자 동작 하나에 한 번만 알림이 나가는지.
 * 알림 자체는 커밋된 뒤에 나가고, Service 는 WebSocket 을 직접 부르지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelPlanScheduleBroadcastTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final Long DAY_ID = 100L;
    private static final Long TARGET_DAY_ID = 200L;
    private static final Long ITEM_ID = 500L;
    private static final Long ALTERNATIVE_ID = 900L;
    private static final Long MEMBER_ID = 11L;
    private static final Integer VERSION = 3;

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @Mock
    private TravelPlanAlternativeMapper travelPlanAlternativeMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private TravelPlanService travelPlanService;

    // ── 동작마다 한 번 ──────────────────────────────────────

    @Test
    void addingAnItemTellsThatOneDay() {
        givenRoom();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any())).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "카페 방문");

        assertThat(captureEvent())
                .isEqualTo(TravelPlanScheduleChangedEvent.ofDay(
                        PLAN_ID, DAY_ID, TravelPlanScheduleChangeType.ITEM_ADDED));
    }

    @Test
    void editingAnItemTellsThatOneDay() {
        givenItem();
        when(travelPlanItemMapper.updateContent(ITEM_ID, DAY_ID, "해변 카페 방문", VERSION))
                .thenReturn(1);

        travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "해변 카페 방문", VERSION);

        TravelPlanScheduleChangedEvent event = captureEvent();
        assertThat(event.travelPlanId()).isEqualTo(PLAN_ID);
        assertThat(event.affectedDayIds()).containsExactly(DAY_ID);
        assertThat(event.changeType()).isEqualTo(TravelPlanScheduleChangeType.ITEM_UPDATED);
    }

    @Test
    void deletingAnItemTellsThatOneDay() {
        givenItem();
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 1)).thenReturn(null);
        when(travelPlanItemMapper.deleteByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(1);

        travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        assertThat(captureEvent().changeType())
                .isEqualTo(TravelPlanScheduleChangeType.ITEM_DELETED);
    }

    @Test
    void promotingAnAlternativeStillTellsThatOneDayOnce() {
        // A 를 지우면 B 가 A 로, C 가 B 로 올라간다.
        // 화면은 그 DAY 를 통째로 다시 읽으므로 승격 결과까지 그대로 보인다.
        givenItem();
        TravelPlanItemAlternative promoted = new TravelPlanItemAlternative();
        promoted.setId(900L);
        promoted.setTravelPlanItemId(ITEM_ID);
        promoted.setContent("창덕궁");
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 1)).thenReturn(promoted);
        when(travelPlanItemMapper.promoteAlternativeContent(
                anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(900L, ITEM_ID)).thenReturn(1);

        travelPlanService.deleteItem(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        // 대안 쪽으로 따로 알리지 않는다. DAY 알림 하나뿐이다
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        assertThat(captureEvent().affectedDayIds()).containsExactly(DAY_ID);
    }

    @Test
    void deletingTheWholeGroupTellsThatOneDay() {
        givenItem();
        when(travelPlanItemMapper.deleteByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(1);

        travelPlanService.deleteItemGroup(USER_ID, PLAN_ID, DAY_ID, ITEM_ID);

        assertThat(captureEvent().changeType())
                .isEqualTo(TravelPlanScheduleChangeType.ITEM_DELETED);
    }

    @Test
    void movingUpOrDownTellsThatOneDayOnceEvenThoughOrderIsWrittenSeveralTimes() {
        givenItem();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(3);
        when(travelPlanItemMapper.findPreviousItem(DAY_ID, 2)).thenReturn(neighbour(1));
        when(travelPlanItemMapper.updateDisplayOrderWithVersion(ITEM_ID, DAY_ID, 1, VERSION))
                .thenReturn(1);

        travelPlanService.moveItemUp(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, VERSION);

        // 안에서 display_order UPDATE 가 세 번 일어나도 알림은 한 번이다
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        TravelPlanScheduleChangedEvent event = captureEvent();
        assertThat(event.affectedDayIds()).containsExactly(DAY_ID);
        assertThat(event.changeType()).isEqualTo(TravelPlanScheduleChangeType.ITEM_REORDERED);
    }

    @Test
    void movingToAnotherDayTellsBothDaysInOneEvent() {
        givenItem();
        givenDay(TARGET_DAY_ID);
        when(travelPlanItemMapper.findMaxDisplayOrder(TARGET_DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.moveToDayWithVersion(
                ITEM_ID, DAY_ID, TARGET_DAY_ID, 1, VERSION)).thenReturn(1);

        travelPlanService.moveItemToDay(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, TARGET_DAY_ID, VERSION);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        TravelPlanScheduleChangedEvent event = captureEvent();
        assertThat(event.affectedDayIds()).containsExactly(DAY_ID, TARGET_DAY_ID);
        assertThat(event.changeType()).isEqualTo(TravelPlanScheduleChangeType.ITEM_MOVED);
    }

    // ── 실패하면 알리지 않는다 ──────────────────────────────

    @Test
    void nothingIsAnnouncedWhenTheChangeDidNotHappen() {
        givenItem();
        // 그 사이 다른 사람이 고쳐 충돌이 났다
        when(travelPlanItemMapper.updateContent(anyLong(), anyLong(), anyString(), anyInt()))
                .thenReturn(0);

        assertThatThrownBy(() -> travelPlanService.updateItem(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, "해변 카페 방문", VERSION))
                .isInstanceOf(TravelPlanConflictException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void whoeverIsNotAnActiveMemberNeverCausesAnAnnouncement() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "카페"))
                .isInstanceOf(RuntimeException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aNewAlternativeIsAnnouncedOnItsOwnDay() {
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(0);
        when(travelPlanAlternativeMapper.insertAlternative(any())).thenReturn(1);

        travelPlanService.addAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어");

        assertThat(captureEvent()).isEqualTo(TravelPlanScheduleChangedEvent.ofDay(
                PLAN_ID, DAY_ID, TravelPlanScheduleChangeType.ALTERNATIVE_ADDED));
    }

    @Test
    void anEditedAlternativeIsAnnounced() {
        givenItem();
        givenAlternative(1);
        when(travelPlanAlternativeMapper.updateWithVersion(
                ALTERNATIVE_ID, ITEM_ID, null, "아쿠아플라넷", VERSION)).thenReturn(1);

        travelPlanService.updateAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID,
                ALTERNATIVE_ID, null, "아쿠아플라넷", VERSION);

        assertThat(captureEvent()).isEqualTo(TravelPlanScheduleChangedEvent.ofDay(
                PLAN_ID, DAY_ID, TravelPlanScheduleChangeType.ALTERNATIVE_UPDATED));
    }

    @Test
    void aDeletedAlternativeIsAnnouncedOnceEvenWhenCMovesUp() {
        // C 가 B 자리로 올라온 결과까지 한 번의 알림으로 그 DAY 를 다시 읽게 한다
        givenItem();
        givenAlternative(1);
        when(travelPlanAlternativeMapper.deleteByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(1);
        when(travelPlanAlternativeMapper.findByItemIdAndOrder(ITEM_ID, 2)).thenReturn(null);

        travelPlanService.deleteAlternative(USER_ID, PLAN_ID, DAY_ID, ITEM_ID, ALTERNATIVE_ID);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        assertThat(captureEvent()).isEqualTo(TravelPlanScheduleChangedEvent.ofDay(
                PLAN_ID, DAY_ID, TravelPlanScheduleChangeType.ALTERNATIVE_DELETED));
    }

    @Test
    void aRejectedAlternativeIsNotAnnounced() {
        // 이미 B/C 가 다 찼으면 저장되지 않으므로 알릴 것도 없다
        givenItem();
        when(travelPlanAlternativeMapper.countByItemId(ITEM_ID)).thenReturn(2);

        assertThatThrownBy(() -> travelPlanService.addAlternative(
                USER_ID, PLAN_ID, DAY_ID, ITEM_ID, null, "카페 투어"))
                .isInstanceOf(RuntimeException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ── 커밋 뒤에만 나간다 ──────────────────────────────────

    @Test
    void theServiceNeverTalksToWebSocketItself() {
        // Service 가 SimpMessagingTemplate 을 들고 있으면 롤백된 변경이 먼저 나갈 수 있다
        assertThat(TravelPlanService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(SimpMessagingTemplate.class);
    }

    @Test
    void theListenerOnlySendsAfterTheTransactionCommits() throws NoSuchMethodException {
        Method handler = TravelPlanScheduleChangedListener.class.getMethod(
                "onScheduleChanged", TravelPlanScheduleChangedEvent.class);

        TransactionalEventListener annotation =
                handler.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        // 롤백되면 아무것도 나가지 않아야 한다
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void theListenerSendsToThatRoomsScheduleTopic() {
        SimpMessagingTemplate messagingTemplate =
                org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        TravelPlanScheduleChangedListener listener =
                new TravelPlanScheduleChangedListener(messagingTemplate);
        TravelPlanScheduleChangedEvent event = TravelPlanScheduleChangedEvent.ofDay(
                PLAN_ID, DAY_ID, TravelPlanScheduleChangeType.ITEM_ADDED);

        listener.onScheduleChanged(event);

        verify(messagingTemplate).convertAndSend("/topic/travel-plans/42/schedule", event);
    }

    // ── 이벤트 값 ───────────────────────────────────────────

    @Test
    void theSameDayIsNeverListedTwice() {
        TravelPlanScheduleChangedEvent event = new TravelPlanScheduleChangedEvent(
                PLAN_ID, List.of(DAY_ID, DAY_ID, TARGET_DAY_ID),
                TravelPlanScheduleChangeType.ITEM_MOVED);

        assertThat(event.affectedDayIds()).containsExactly(DAY_ID, TARGET_DAY_ID);
    }

    @Test
    void theEventCarriesASignalNotTheContent() {
        // 화면에 그릴 값은 클라이언트가 서버에서 다시 읽는다. DB 가 최종 기준이다
        assertThat(TravelPlanScheduleChangedEvent.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("travelPlanId", "affectedDayIds", "changeType");
    }

    private TravelPlanScheduleChangedEvent captureEvent() {
        ArgumentCaptor<TravelPlanScheduleChangedEvent> captor =
                ArgumentCaptor.forClass(TravelPlanScheduleChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private TravelPlanItem neighbour(int displayOrder) {
        TravelPlanItem item = new TravelPlanItem();
        item.setId(600L);
        item.setTravelPlanDayId(DAY_ID);
        item.setDisplayOrder(displayOrder);
        item.setVersion(1);
        return item;
    }

    private void givenAlternative(int alternativeOrder) {
        TravelPlanItemAlternative alternative = new TravelPlanItemAlternative();
        alternative.setId(ALTERNATIVE_ID);
        alternative.setTravelPlanItemId(ITEM_ID);
        alternative.setAlternativeOrder(alternativeOrder);
        alternative.setContent("아쿠아플라넷");
        alternative.setVersion(VERSION);
        when(travelPlanAlternativeMapper.findByIdAndItemId(ALTERNATIVE_ID, ITEM_ID))
                .thenReturn(alternative);
    }

    private void givenItem() {
        givenRoom();
        TravelPlanItem item = new TravelPlanItem();
        item.setId(ITEM_ID);
        item.setTravelPlanDayId(DAY_ID);
        item.setContent("카페 방문");
        item.setDisplayOrder(2);
        item.setCreatedByMemberId(MEMBER_ID);
        item.setVersion(VERSION);
        when(travelPlanItemMapper.findByIdAndDayId(ITEM_ID, DAY_ID)).thenReturn(item);
    }

    private void givenRoom() {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(MEMBER_ID);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setRole(TravelPlanRole.MEMBER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE"))
                .thenReturn(member);

        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        // 일정을 고치는 길은 방 row 를 잠그고 읽는다(완료 처리와 한 줄로 서기 위해)
        when(travelPlanMapper.findPlanByIdAndStatusForUpdate(PLAN_ID, "ACTIVE")).thenReturn(plan);

        givenDay(DAY_ID);
    }

    private void givenDay(Long dayId) {
        TravelPlanDay day = new TravelPlanDay();
        day.setId(dayId);
        day.setTravelPlanId(PLAN_ID);
        day.setDayNumber(1);
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, dayId)).thenReturn(day);
    }
}
