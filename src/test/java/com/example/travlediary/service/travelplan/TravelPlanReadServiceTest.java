package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanDetailDto;
import com.example.travlediary.dto.TravelPlanListItemDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 목록/상세 조회는 항상 현재 사용자 기준이며,
 * 방 id 만 안다고 다른 사람의 방을 읽을 수 없어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanReadServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAN_ID = 42L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Mock
    private TravelPlanMapper travelPlanMapper;
    @Mock
    private TravelPlanItemMapper travelPlanItemMapper;
    @InjectMocks
    private TravelPlanService travelPlanService;

    @Test
    void listReadsOnlyTheCurrentUsersActiveRooms() {
        TravelPlanListItemDto row = new TravelPlanListItemDto();
        row.setTravelPlanId(PLAN_ID);
        row.setRole(TravelPlanRole.OWNER);
        row.setMemberCount(1);
        when(travelPlanMapper.findActivePlansByUserId(USER_ID, "ACTIVE", "ACTIVE"))
                .thenReturn(List.of(row));

        assertThat(travelPlanService.getActivePlans(USER_ID)).containsExactly(row);

        // 상태 값은 enum 이름을 그대로 넘긴다
        verify(travelPlanMapper).findActivePlansByUserId(
                USER_ID, TravelPlanStatus.ACTIVE.name(), TravelPlanMemberStatus.ACTIVE.name());
    }

    @Test
    void anEmptyListComesBackAsAnEmptyCollection() {
        when(travelPlanMapper.findActivePlansByUserId(anyLong(), anyString(), anyString()))
                .thenReturn(null);

        assertThat(travelPlanService.getActivePlans(USER_ID)).isEmpty();
    }

    @Test
    void detailReturnsThePlanTheCurrentMemberAndTheDaysInOrder() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan());
        when(travelPlanMapper.findDaysByPlanId(PLAN_ID))
                .thenReturn(List.of(day(1, START), day(2, START.plusDays(1))));

        TravelPlanDetailDto detail = travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID);

        assertThat(detail.getPlan().getId()).isEqualTo(PLAN_ID);
        assertThat(detail.getCurrentMember().getRole()).isEqualTo(TravelPlanRole.OWNER);
        assertThat(detail.getCurrentMember().getDisplayName()).isEqualTo("민준");
        assertThat(detail.getDays())
                .extracting(TravelPlanDay::getDayNumber)
                .containsExactly(1, 2);
    }

    @Test
    void detailGroupsItemsByTheirDayWithoutMixingThem() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan());
        when(travelPlanMapper.findDaysByPlanId(PLAN_ID))
                .thenReturn(List.of(day(100L, 1, START), day(200L, 2, START.plusDays(1))));
        // 방 전체 일정을 한 번에 읽는다 (DAY 수만큼 조회하지 않는다)
        when(travelPlanItemMapper.findByPlanId(PLAN_ID)).thenReturn(List.of(
                item(100L, 1, "DAY1 첫 일정"),
                item(100L, 2, "DAY1 둘째 일정"),
                item(200L, 1, "DAY2 첫 일정")));

        TravelPlanDetailDto detail = travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID);

        assertThat(detail.getItemsByDayId().get(100L))
                .extracting(TravelPlanItem::getContent)
                .containsExactly("DAY1 첫 일정", "DAY1 둘째 일정");
        assertThat(detail.getItemsByDayId().get(200L))
                .extracting(TravelPlanItem::getContent)
                .containsExactly("DAY2 첫 일정");
        verify(travelPlanItemMapper).findByPlanId(PLAN_ID);
    }

    @Test
    void aDayWithoutItemsSimplyHasNoEntry() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan());
        when(travelPlanMapper.findDaysByPlanId(PLAN_ID)).thenReturn(List.of(day(100L, 1, START)));
        when(travelPlanItemMapper.findByPlanId(PLAN_ID)).thenReturn(null);

        assertThat(travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID).getItemsByDayId())
                .doesNotContainKey(100L);
    }

    @Test
    void aRoomWithoutDaysStillLoads() {
        givenActiveMembership();
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(plan());
        when(travelPlanMapper.findDaysByPlanId(PLAN_ID)).thenReturn(null);

        assertThat(travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID).getDays()).isEmpty();
    }

    @Test
    void someoneElsesRoomLooksLikeItDoesNotExist() {
        // 멤버 조회가 비면 접근 권한이 없다는 뜻이다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        // 권한이 없으면 방 정보 자체를 읽지 않는다
        verify(travelPlanMapper, never()).findPlanByIdAndStatus(anyLong(), anyString());
        verify(travelPlanMapper, never()).findDaysByPlanId(anyLong());
    }

    @Test
    void aLeftOrRemovedMemberCannotOpenTheRoom() {
        // LEFT/REMOVED 는 status=ACTIVE 조건에 걸려 조회되지 않는다
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        verify(travelPlanMapper).findMemberByPlanAndUser(
                PLAN_ID, USER_ID, TravelPlanMemberStatus.ACTIVE.name());
    }

    @Test
    void aRoomThatIsNoLongerActiveIsNotReadable() {
        givenActiveMembership();
        // 방 상태가 ACTIVE 가 아니면 조회 결과가 비어 온다
        when(travelPlanMapper.findPlanByIdAndStatus(PLAN_ID, "ACTIVE")).thenReturn(null);

        assertThatThrownBy(() -> travelPlanService.getActivePlanDetail(USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        verify(travelPlanMapper, never()).findDaysByPlanId(anyLong());
    }

    @Test
    void aMissingRoomIdIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> travelPlanService.getActivePlanDetail(USER_ID, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(travelPlanMapper, never()).findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
    }

    @Test
    void anonymousAccessIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> travelPlanService.getActivePlans(null))
                .isInstanceOf(TravelPlanValidationException.class);
        assertThatThrownBy(() -> travelPlanService.getActivePlanDetail(null, PLAN_ID))
                .isInstanceOf(TravelPlanValidationException.class);

        verify(travelPlanMapper, never()).findActivePlansByUserId(anyLong(), anyString(), anyString());
        verify(travelPlanMapper, never()).findMemberByPlanAndUser(anyLong(), anyLong(), anyString());
    }

    private void givenActiveMembership() {
        TravelPlanMember member = new TravelPlanMember();
        member.setId(11L);
        member.setTravelPlanId(PLAN_ID);
        member.setUserId(USER_ID);
        member.setDisplayName("민준");
        member.setRole(TravelPlanRole.OWNER);
        member.setStatus(TravelPlanMemberStatus.ACTIVE);
        when(travelPlanMapper.findMemberByPlanAndUser(PLAN_ID, USER_ID, "ACTIVE")).thenReturn(member);
    }

    private TravelPlan plan() {
        TravelPlan plan = new TravelPlan();
        plan.setId(PLAN_ID);
        plan.setTitle("제주 여행");
        plan.setStartDate(START);
        plan.setEndDate(START.plusDays(1));
        plan.setStatus(TravelPlanStatus.ACTIVE);
        return plan;
    }

    private TravelPlanDay day(int dayNumber, LocalDate planDate) {
        return day(null, dayNumber, planDate);
    }

    private TravelPlanDay day(Long id, int dayNumber, LocalDate planDate) {
        TravelPlanDay day = new TravelPlanDay();
        day.setId(id);
        day.setTravelPlanId(PLAN_ID);
        day.setDayNumber(dayNumber);
        day.setPlanDate(planDate);
        return day;
    }

    private TravelPlanItem item(Long dayId, int displayOrder, String content) {
        TravelPlanItem item = new TravelPlanItem();
        item.setTravelPlanDayId(dayId);
        item.setDisplayOrder(displayOrder);
        item.setContent(content);
        return item;
    }
}
