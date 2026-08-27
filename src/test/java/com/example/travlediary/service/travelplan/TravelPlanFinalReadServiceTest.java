package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanFinalDetailDto;
import com.example.travlediary.dto.TravelPlanFinalListItemDto;
import com.example.travlediary.model.TravelPlanFinalDay;
import com.example.travlediary.model.TravelPlanFinalItem;
import com.example.travlediary.model.TravelPlanFinalItemAlternative;
import com.example.travlediary.model.TravelPlanFinalMember;
import com.example.travlediary.model.TravelPlanFinalSnapshot;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.repository.travelplan.TravelPlanFinalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 완료된 여행 읽기.
 *
 * <p>최종본만 본다. 원본 방을 다시 들여다보지 않으므로
 * 나중에 원본이 어떻게 되든 완료된 여행은 그때 모습 그대로 보인다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelPlanFinalReadServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final Long PLAN_ID = 42L;
    private static final Long SNAPSHOT_ID = 900L;
    private static final Long DAY_ONE = 910L;
    private static final Long DAY_TWO = 911L;
    private static final Long ITEM_ONE = 920L;

    @Mock
    private TravelPlanFinalMapper travelPlanFinalMapper;
    @InjectMocks
    private TravelPlanFinalReadService readService;

    // ── 목록 ────────────────────────────────────────────────

    @Test
    void theListOnlyHasTripsIWasOn() {
        when(travelPlanFinalMapper.findSnapshotsByUserId(USER_ID))
                .thenReturn(List.of(listItem("제주도 여행", 3)));

        List<TravelPlanFinalListItemDto> plans = readService.getCompletedPlans(USER_ID);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).getTitle()).isEqualTo("제주도 여행");
        assertThat(plans.get(0).getMemberCount()).isEqualTo(3);
        assertThat(plans.get(0).getTravelPlanId()).isEqualTo(PLAN_ID);
    }

    @Test
    void everyoneWhoWasOnTheTripSeesItInTheirOwnList() {
        // 방장이든 아니든 각자 자기 계정에서 같은 완료 여행이 보인다
        when(travelPlanFinalMapper.findSnapshotsByUserId(USER_ID))
                .thenReturn(List.of(listItem("제주도 여행", 2)));
        when(travelPlanFinalMapper.findSnapshotsByUserId(OTHER_USER_ID))
                .thenReturn(List.of(listItem("제주도 여행", 2)));

        assertThat(readService.getCompletedPlans(USER_ID)).hasSize(1);
        assertThat(readService.getCompletedPlans(OTHER_USER_ID)).hasSize(1);
        assertThat(readService.getCompletedPlans(OTHER_USER_ID).get(0).getSnapshotId())
                .isEqualTo(readService.getCompletedPlans(USER_ID).get(0).getSnapshotId());
    }

    @Test
    void aFinishedTripSomeoneWasNotOnStaysOutOfTheirList() {
        /*
          최종 명단에 없으면 조회가 비어 온다.
          최종본이 있다는 것 자체가 목록에 나오는 이유가 되지는 않는다.
        */
        when(travelPlanFinalMapper.findSnapshotsByUserId(OTHER_USER_ID)).thenReturn(List.of());

        assertThat(readService.getCompletedPlans(OTHER_USER_ID)).isEmpty();
    }

    @Test
    void someoneWithNoFinishedTripsSeesAnEmptyList() {
        when(travelPlanFinalMapper.findSnapshotsByUserId(USER_ID)).thenReturn(List.of());

        assertThat(readService.getCompletedPlans(USER_ID)).isEmpty();
        assertThat(readService.getCompletedPlans(null)).isEmpty();
    }

    // ── 상세 ────────────────────────────────────────────────

    @Test
    void theWholeTripComesBackFromTheFinalCopy() {
        givenSnapshotFor(USER_ID);

        TravelPlanFinalDetailDto detail =
                readService.getCompletedPlanDetail(USER_ID, PLAN_ID);

        assertThat(detail.getSnapshot().getTitle()).isEqualTo("제주도 여행");
        assertThat(detail.getMembers())
                .extracting(TravelPlanFinalMember::getDisplayName)
                .containsExactly("민준", "쭈니");
        assertThat(detail.getDays())
                .extracting(TravelPlanFinalDay::getDayNumber)
                .containsExactly(1, 2);
    }

    @Test
    void theDaysAndPlansKeepTheOrderTheyWereFinishedIn() {
        givenSnapshotFor(USER_ID);

        TravelPlanFinalDetailDto detail =
                readService.getCompletedPlanDetail(USER_ID, PLAN_ID);

        // 첫날의 일정이 적어 둔 차례 그대로 온다
        assertThat(detail.getItemsByDayId().get(DAY_ONE))
                .extracting(TravelPlanFinalItem::getContent)
                .containsExactly("경복궁", "북촌");
        assertThat(detail.getItemsByDayId().get(DAY_TWO))
                .extracting(TravelPlanFinalItem::getContent)
                .containsExactly("성산일출봉");
        // B 다음 C 순서도 그대로다
        assertThat(detail.getAlternativesByItemId().get(ITEM_ONE))
                .extracting(TravelPlanFinalItemAlternative::getAlternativeOrder,
                        TravelPlanFinalItemAlternative::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "아쿠아플라넷"),
                        org.assertj.core.groups.Tuple.tuple(2, "카페"));
    }

    @Test
    void anItemWithoutAlternativesSimplyHasNone() {
        givenSnapshotFor(USER_ID);

        assertThat(readService.getCompletedPlanDetail(USER_ID, PLAN_ID)
                .getAlternativesByItemId())
                .doesNotContainKey(921L);
    }

    @Test
    void everyoneWhoWasThereSeesTheSameCopy() {
        // 방장이든 아니든 같은 최종본이다. 역할에 따라 달라지는 것이 없다
        givenSnapshotFor(USER_ID);
        givenSnapshotFor(OTHER_USER_ID);

        TravelPlanFinalDetailDto mine = readService.getCompletedPlanDetail(USER_ID, PLAN_ID);
        TravelPlanFinalDetailDto theirs =
                readService.getCompletedPlanDetail(OTHER_USER_ID, PLAN_ID);

        assertThat(theirs.getSnapshot().getId()).isEqualTo(mine.getSnapshot().getId());
        assertThat(theirs.getItemsByDayId()).isEqualTo(mine.getItemsByDayId());
        assertThat(theirs.getMembers()).hasSameSizeAs(mine.getMembers());
    }

    @Test
    void someoneWhoWasNotOnTheTripIsNotEvenToldItExists() {
        // 함께하지 않았으면 조회 자체가 비어 온다
        when(travelPlanFinalMapper.findSnapshotByPlanAndUser(PLAN_ID, OTHER_USER_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> readService.getCompletedPlanDetail(OTHER_USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        // 볼 자격이 없으면 안쪽은 읽지도 않는다
        verify(travelPlanFinalMapper, never()).findDaysBySnapshotId(anyLong());
        verify(travelPlanFinalMapper, never()).findItemsBySnapshotId(anyLong());
    }

    @Test
    void theFinishedTripNeverLooksAtTheLivingRoomAgain() {
        /*
          최종본은 한번 만들어지면 바뀌지 않는다.
          원본 방을 들고 있지 않으니 원본이 어떻게 되든 그때 모습 그대로 보인다.
        */
        assertThat(TravelPlanFinalReadService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .extracting(Class::getSimpleName)
                .containsExactly("TravelPlanFinalMapper");
    }

    @Test
    void whatSomeoneClearedStopsComingBackForThem() {
        /*
          지우기 자체는 TravelPlanFinalDeleteService 가 맡는다.
          여기서 보는 것은 지워진 뒤의 읽기다 — 조회가 비어 오면 그대로 막힌다.
        */
        when(travelPlanFinalMapper.findSnapshotByPlanAndUser(PLAN_ID, USER_ID)).thenReturn(null);
        when(travelPlanFinalMapper.findSnapshotsByUserId(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> readService.getCompletedPlanDetail(USER_ID, PLAN_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(readService.getCompletedPlans(USER_ID)).isEmpty();

        // 함께한 사람은 그대로 본다
        givenSnapshotFor(OTHER_USER_ID);
        when(travelPlanFinalMapper.findSnapshotsByUserId(OTHER_USER_ID))
                .thenReturn(List.of(listItem("제주도 여행", 2)));
        assertThat(readService.getCompletedPlanDetail(OTHER_USER_ID, PLAN_ID)
                .getSnapshot().getId()).isEqualTo(SNAPSHOT_ID);
        assertThat(readService.getCompletedPlans(OTHER_USER_ID)).hasSize(1);
    }

    // ── 준비 ────────────────────────────────────────────────

    private void givenSnapshotFor(Long userId) {
        TravelPlanFinalSnapshot snapshot = new TravelPlanFinalSnapshot();
        snapshot.setId(SNAPSHOT_ID);
        snapshot.setTravelPlanId(PLAN_ID);
        snapshot.setTitle("제주도 여행");
        snapshot.setStartDate(LocalDate.of(2026, 9, 13));
        snapshot.setEndDate(LocalDate.of(2026, 9, 14));
        when(travelPlanFinalMapper.findSnapshotByPlanAndUser(PLAN_ID, userId))
                .thenReturn(snapshot);

        when(travelPlanFinalMapper.findMembersBySnapshotId(SNAPSHOT_ID))
                .thenReturn(List.of(member("민준", TravelPlanRole.OWNER),
                        member("쭈니", TravelPlanRole.MEMBER)));
        when(travelPlanFinalMapper.findDaysBySnapshotId(SNAPSHOT_ID))
                .thenReturn(List.of(day(DAY_ONE, 1), day(DAY_TWO, 2)));
        when(travelPlanFinalMapper.findItemsBySnapshotId(SNAPSHOT_ID))
                .thenReturn(List.of(item(ITEM_ONE, DAY_ONE, "경복궁", 1),
                        item(921L, DAY_ONE, "북촌", 2),
                        item(922L, DAY_TWO, "성산일출봉", 1)));
        when(travelPlanFinalMapper.findAlternativesBySnapshotId(SNAPSHOT_ID))
                .thenReturn(List.of(alternative(ITEM_ONE, 1, "아쿠아플라넷"),
                        alternative(ITEM_ONE, 2, "카페")));
    }

    private TravelPlanFinalListItemDto listItem(String title, int memberCount) {
        TravelPlanFinalListItemDto dto = new TravelPlanFinalListItemDto();
        dto.setTravelPlanId(PLAN_ID);
        dto.setSnapshotId(SNAPSHOT_ID);
        dto.setTitle(title);
        dto.setStartDate(LocalDate.of(2026, 9, 13));
        dto.setEndDate(LocalDate.of(2026, 9, 14));
        dto.setMemberCount(memberCount);
        return dto;
    }

    private TravelPlanFinalMember member(String displayName, TravelPlanRole role) {
        TravelPlanFinalMember member = new TravelPlanFinalMember();
        member.setSnapshotId(SNAPSHOT_ID);
        member.setDisplayName(displayName);
        member.setRole(role);
        return member;
    }

    private TravelPlanFinalDay day(Long id, int dayNumber) {
        TravelPlanFinalDay day = new TravelPlanFinalDay();
        day.setId(id);
        day.setSnapshotId(SNAPSHOT_ID);
        day.setDayNumber(dayNumber);
        day.setPlanDate(LocalDate.of(2026, 9, 12).plusDays(dayNumber));
        return day;
    }

    private TravelPlanFinalItem item(Long id, Long dayId, String content, int displayOrder) {
        TravelPlanFinalItem item = new TravelPlanFinalItem();
        item.setId(id);
        item.setFinalDayId(dayId);
        item.setContent(content);
        item.setDisplayOrder(displayOrder);
        return item;
    }

    private TravelPlanFinalItemAlternative alternative(Long itemId, int order, String content) {
        TravelPlanFinalItemAlternative alternative = new TravelPlanFinalItemAlternative();
        alternative.setFinalItemId(itemId);
        alternative.setAlternativeOrder(order);
        alternative.setContent(content);
        return alternative;
    }
}
