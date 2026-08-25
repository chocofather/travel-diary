package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanDayDetailDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DAY 편집: 방 접근 권한에 더해 dayId 가 그 방 소속인지까지 확인한 뒤에만 읽고 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanDayServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final Long DAY_ID = 100L;
    private static final Long MEMBER_ID = 11L;

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

    @Test
    void dayDetailReturnsThePlanTheMemberTheDayAndItsItems() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findByDayId(DAY_ID))
                .thenReturn(List.of(item(1, "첫 일정"), item(2, "둘째 일정")));

        TravelPlanDayDetailDto detail =
                travelPlanService.getActiveDayDetail(USER_ID, PLAN_ID, DAY_ID);

        assertThat(detail.getPlan().getId()).isEqualTo(PLAN_ID);
        assertThat(detail.getCurrentMember().getId()).isEqualTo(MEMBER_ID);
        assertThat(detail.getDay().getId()).isEqualTo(DAY_ID);
        assertThat(detail.getItems())
                .extracting(TravelPlanItem::getDisplayOrder, TravelPlanItem::getContent)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(1, "첫 일정"),
                        org.assertj.core.api.Assertions.tuple(2, "둘째 일정"));
    }

    @Test
    void aDayWithoutItemsStillLoads() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findByDayId(DAY_ID)).thenReturn(null);

        assertThat(travelPlanService.getActiveDayDetail(USER_ID, PLAN_ID, DAY_ID).getItems())
                .isEmpty();
    }

    @Test
    void aDayFromAnotherPlanIsNotReachable() {
        givenActiveMembership();
        givenActivePlan();
        // 방 소속 조건이 걸려 다른 방의 dayId 는 조회되지 않는다
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActiveDayDetail(USER_ID, PLAN_ID, DAY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(travelPlanItemMapper);
    }

    @Test
    void someoneElsesRoomIsRejectedBeforeTheDayIsEvenLookedUp() {
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActiveDayDetail(USER_ID, PLAN_ID, DAY_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findDayByPlanAndId(anyLong(), anyLong());
        verifyNoInteractions(travelPlanItemMapper);
    }

    @Test
    void aRoomThatIsNoLongerActiveIsNotEditable() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActiveDayDetail(USER_ID, PLAN_ID, DAY_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "일정"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(travelPlanItemMapper);
    }

    @Test
    void theFirstItemOfADayGetsOrderOne() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "첫 일정");

        assertThat(capturedItem().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void aNewItemGoesAfterTheLastOne() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(3);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "네 번째 일정");

        assertThat(capturedItem().getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void theAuthorComesFromTheServerNotTheRequest() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "일정");

        TravelPlanItem saved = capturedItem();
        assertThat(saved.getTravelPlanDayId()).isEqualTo(DAY_ID);
        // 현재 로그인 사용자의 방 참여 id 를 그대로 쓴다
        assertThat(saved.getCreatedByMemberId()).isEqualTo(MEMBER_ID);
        // 태그 UI 가 없어 항상 null, version 은 DB DEFAULT 에 맡긴다
        assertThat(saved.getTag()).isNull();
        assertThat(saved.getVersion()).isNull();
    }

    @Test
    void contentIsTrimmedButInnerLineBreaksSurvive() {
        givenAccessibleDay();
        when(travelPlanItemMapper.findMaxDisplayOrder(DAY_ID)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID,
                "  오전 10시 경복궁 도착\n한복 빌리고 천천히 둘러보기  ");

        assertThat(capturedItem().getContent())
                .isEqualTo("오전 10시 경복궁 도착\n한복 빌리고 천천히 둘러보기");
    }

    @Test
    void blankContentIsRejectedWithoutInserting() {
        givenAccessibleDay();

        for (String content : new String[]{null, "", "   ", "\n\n"}) {
            assertThatThrownBy(() -> travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, content))
                    .as("content=%s", content)
                    .isInstanceOf(TravelPlanValidationException.class)
                    .extracting("field").isEqualTo("content");
        }
        verify(travelPlanItemMapper, never()).insertItem(any());
        verify(travelPlanItemMapper, never()).findMaxDisplayOrder(anyLong());
    }

    @Test
    void addingAnItemToAnotherPlansDayIsRejected() {
        givenActiveMembership();
        givenActivePlan();
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, DAY_ID)).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.addItem(USER_ID, PLAN_ID, DAY_ID, "일정"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(travelPlanItemMapper);
    }

    @Test
    void anonymousWritesAreRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> travelPlanService.addItem(null, PLAN_ID, DAY_ID, "일정"))
                .isInstanceOf(TravelPlanValidationException.class);

        verify(travelPlanMapper, never())
                .findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
        verifyNoInteractions(travelPlanItemMapper);
    }

    @Test
    void eachDayKeepsItsOwnOrderSequence() {
        // DAY 1 에 3개가 있어도 DAY 2 의 첫 일정은 1부터 시작한다
        Long otherDayId = 200L;
        givenActiveMembership();
        givenActivePlan();
        TravelPlanDay otherDay = new TravelPlanDay();
        otherDay.setId(otherDayId);
        otherDay.setTravelPlanId(PLAN_ID);
        otherDay.setDayNumber(2);
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, otherDayId)).thenReturn(otherDay);
        when(travelPlanItemMapper.findMaxDisplayOrder(otherDayId)).thenReturn(0);
        when(travelPlanItemMapper.insertItem(any(TravelPlanItem.class))).thenReturn(1);

        travelPlanService.addItem(USER_ID, PLAN_ID, otherDayId, "DAY 2 첫 일정");

        // 순서는 요청한 DAY 만 기준으로 계산한다
        verify(travelPlanItemMapper).findMaxDisplayOrder(otherDayId);
        verify(travelPlanItemMapper, never()).findMaxDisplayOrder(DAY_ID);
        TravelPlanItem saved = capturedItem();
        assertThat(saved.getTravelPlanDayId()).isEqualTo(otherDayId);
        assertThat(saved.getDisplayOrder()).isEqualTo(1);
    }

    private void givenAccessibleDay() {
        givenActiveMembership();
        givenActivePlan();
        TravelPlanDay day = new TravelPlanDay();
        day.setId(DAY_ID);
        day.setTravelPlanId(PLAN_ID);
        day.setDayNumber(1);
        day.setPlanDate(LocalDate.of(2026, 9, 13));
        when(travelPlanMapper.findDayByPlanAndId(PLAN_ID, DAY_ID)).thenReturn(day);
    }

    private void givenActiveMembership() {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(MEMBER_ID);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setDisplayName("민준");
        member.setRole(TravelPlanRole.OWNER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(member);
    }

    private void givenActivePlan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setTitle("제주 여행");
        plan.setStatus(TravelPlanStatus.ACTIVE);
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan);
    }

    private TravelPlanItem capturedItem() {
        ArgumentCaptor<TravelPlanItem> captor = ArgumentCaptor.forClass(TravelPlanItem.class);
        verify(travelPlanItemMapper).insertItem(captor.capture());
        return captor.getValue();
    }

    private TravelPlanItem item(int displayOrder, String content) {
        TravelPlanItem item = new TravelPlanItem();
        item.setDisplayOrder(displayOrder);
        item.setContent(content);
        return item;
    }
}
