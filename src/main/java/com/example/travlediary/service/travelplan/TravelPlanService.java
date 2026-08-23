package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanDayDetailDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravelPlanService {

    /** travel_plans.title 은 varchar(150) */
    private static final int MAX_TITLE_LENGTH = 150;
    /** travel_plan_members.display_name 은 varchar(50) */
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;
    /** 시작일과 종료일을 포함한 최대 여행 일수. DB 제약이 아니라 서비스 정책이다. */
    private static final int MAX_PLAN_DAYS = 90;

    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanItemMapper travelPlanItemMapper;

    /**
     * 현재 사용자가 ACTIVE 멤버로 참여 중인 ACTIVE 방 목록.
     * 호출자가 다른 사용자의 목록을 볼 수 없도록 항상 userId 기준으로만 읽는다.
     */
    @Transactional(readOnly = true)
    public List<TravelPlanListItemDto> getActivePlans(Long userId) {
        requireUser(userId);
        List<TravelPlanListItemDto> plans = travelPlanMapper.findActivePlansByUserId(
                userId, TravelPlanStatus.ACTIVE.name(), TravelPlanMemberStatus.ACTIVE.name());
        return plans == null ? List.of() : plans;
    }

    /**
     * 방 기본 상세.
     * 방이 ACTIVE 이고 현재 사용자가 그 방의 ACTIVE 멤버일 때만 돌려준다.
     * 그 외에는 방의 존재 자체를 알리지 않도록 404 로 처리한다(다이어리와 같은 관례).
     */
    @Transactional(readOnly = true)
    public TravelPlanDetailDto getActivePlanDetail(Long userId, Long travelPlanId) {
        PlanAccess access = requireActiveAccess(userId, travelPlanId);

        List<TravelPlanDay> days = travelPlanMapper.findDaysByPlanId(travelPlanId);
        // DAY 마다 조회하지 않고 방 전체 일정을 한 번에 읽어 DAY 별로 묶는다.
        List<TravelPlanItem> items = travelPlanItemMapper.findByPlanId(travelPlanId);
        Map<Long, List<TravelPlanItem>> itemsByDayId = items == null
                ? Map.of()
                : items.stream().collect(Collectors.groupingBy(
                        TravelPlanItem::getTravelPlanDayId, LinkedHashMap::new, Collectors.toList()));

        return new TravelPlanDetailDto(
                access.plan(), access.member(), days == null ? List.of() : days, itemsByDayId);
    }

    /**
     * DAY 편집 화면 한 벌.
     * 방 접근 권한에 더해 dayId 가 그 방 소속인지까지 확인한다.
     */
    @Transactional(readOnly = true)
    public TravelPlanDayDetailDto getActiveDayDetail(Long userId, Long travelPlanId, Long dayId) {
        PlanAccess access = requireActiveAccess(userId, travelPlanId);
        TravelPlanDay day = requireDayOfPlan(travelPlanId, dayId);

        List<TravelPlanItem> items = travelPlanItemMapper.findByDayId(dayId);
        return new TravelPlanDayDetailDto(
                access.plan(), access.member(), day, items == null ? List.of() : items);
    }

    /**
     * DAY 마지막에 A 일정을 추가한다.
     * 작성자는 요청 값을 믿지 않고 현재 사용자의 방 참여 정보에서 가져온다.
     */
    @Transactional
    public void addItem(Long userId, Long travelPlanId, Long dayId, String content) {
        PlanAccess access = requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);

        String normalizedContent = requiredContent(content);

        TravelPlanItem item = new TravelPlanItem();
        item.setTravelPlanDayId(dayId);
        item.setContent(normalizedContent);
        // 태그 UI 는 아직 없다.
        item.setTag(null);
        item.setCreatedByMemberId(access.member().getId());
        // 같은 트랜잭션 안에서 마지막 순서를 읽어 뒤에 붙인다.
        item.setDisplayOrder(travelPlanItemMapper.findMaxDisplayOrder(dayId) + 1);

        if (travelPlanItemMapper.insertItem(item) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "일정을 저장하지 못했습니다.");
        }
    }

    /** 방이 ACTIVE 이고 현재 사용자가 그 방의 ACTIVE 멤버인지 확인한다. */
    private PlanAccess requireActiveAccess(Long userId, Long travelPlanId) {
        requireUser(userId);
        if (travelPlanId == null) {
            throw planNotFound();
        }

        TravelPlanMember currentMember = travelPlanMapper.findMemberByPlanAndUser(
                travelPlanId, userId, TravelPlanMemberStatus.ACTIVE.name());
        if (currentMember == null) {
            throw planNotFound();
        }
        TravelPlan plan = travelPlanMapper.findPlanByIdAndStatus(
                travelPlanId, TravelPlanStatus.ACTIVE.name());
        if (plan == null) {
            throw planNotFound();
        }
        return new PlanAccess(plan, currentMember);
    }

    /** 다른 방의 dayId 를 URL 에 섞어 넣어도 통과하지 못하게 한다. */
    private TravelPlanDay requireDayOfPlan(Long travelPlanId, Long dayId) {
        TravelPlanDay day = dayId == null
                ? null : travelPlanMapper.findDayByPlanAndId(travelPlanId, dayId);
        if (day == null) {
            throw planNotFound();
        }
        return day;
    }

    /** 자유 텍스트. 양끝 공백만 정리하고 내부 줄바꿈은 그대로 둔다. */
    private String requiredContent(String value) {
        String content = value == null ? "" : value.trim();
        if (content.isEmpty()) {
            throw new TravelPlanValidationException("content", "일정 내용을 입력해 주세요.");
        }
        return content;
    }

    /** 접근 확인 결과. 방과 현재 사용자의 참여 정보를 함께 들고 다닌다. */
    private record PlanAccess(TravelPlan plan, TravelPlanMember member) {
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new TravelPlanValidationException("userId", "로그인이 필요합니다.");
        }
    }

    private ResponseStatusException planNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다.");
    }

    /**
     * 공동 여행계획 방을 만든다.
     * 방 / OWNER 참여자 / 기간만큼의 DAY 를 한 트랜잭션에서 저장한다.
     *
     * @param displayName 이 방에서만 쓰는 생성자의 표시 이름
     * @return 생성된 travel_plans.id
     */
    @Transactional
    public Long createPlan(Long userId, String title, LocalDate startDate, LocalDate endDate,
                           String displayName) {
        requireUser(userId);
        String normalizedTitle = requiredText(
                "title", title, MAX_TITLE_LENGTH, "여행계획 이름", "여행계획 이름을 입력해 주세요.");
        String normalizedDisplayName = requiredText(
                "displayName", displayName, MAX_DISPLAY_NAME_LENGTH,
                "표시 이름", "이 방에서 사용할 표시 이름을 입력해 주세요.");
        int dayCount = requiredPeriod(startDate, endDate);

        TravelPlan plan = new TravelPlan();
        plan.setCreatedByUserId(userId);
        plan.setTitle(normalizedTitle);
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setStatus(TravelPlanStatus.ACTIVE);
        // 대표 이미지는 아직 업로드 기능이 없어 비워 둔다.
        plan.setRepresentativeImageUrl(null);
        if (travelPlanMapper.insertPlan(plan) != 1 || plan.getId() == null) {
            throw saveFailed();
        }
        Long travelPlanId = plan.getId();

        TravelPlanMember owner = new TravelPlanMember();
        owner.setTravelPlanId(travelPlanId);
        owner.setUserId(userId);
        owner.setDisplayName(normalizedDisplayName);
        owner.setRole(TravelPlanRole.OWNER);
        owner.setStatus(TravelPlanMemberStatus.ACTIVE);
        if (travelPlanMapper.insertMember(owner) != 1) {
            throw saveFailed();
        }

        List<TravelPlanDay> days = buildDays(startDate, dayCount);
        // 여러 행을 한 문장으로 넣으므로 영향 행 수가 DAY 수와 같아야 한다.
        if (travelPlanMapper.insertDays(travelPlanId, days) != days.size()) {
            throw saveFailed();
        }
        return travelPlanId;
    }

    /** DAY 1 = startDate 부터 하루씩. 개수는 검증 단계에서 구한 값을 그대로 쓴다. */
    private List<TravelPlanDay> buildDays(LocalDate startDate, int dayCount) {
        List<TravelPlanDay> days = new ArrayList<>(dayCount);
        for (int index = 0; index < dayCount; index++) {
            TravelPlanDay day = new TravelPlanDay();
            day.setDayNumber(index + 1);
            day.setPlanDate(startDate.plusDays(index));
            days.add(day);
        }
        return days;
    }

    private String requiredText(String field, String value, int maxLength,
                                String label, String blankMessage) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new TravelPlanValidationException(field, blankMessage);
        }
        if (text.length() > maxLength) {
            throw new TravelPlanValidationException(field,
                    label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return text;
    }

    /** @return 시작일과 종료일을 포함한 여행 일수 */
    private int requiredPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new TravelPlanValidationException("startDate", "여행 시작일을 선택해 주세요.");
        }
        if (endDate == null) {
            throw new TravelPlanValidationException("endDate", "여행 종료일을 선택해 주세요.");
        }
        if (endDate.isBefore(startDate)) {
            throw new TravelPlanValidationException("endDate", "여행 종료일은 시작일 이후여야 합니다.");
        }

        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount > MAX_PLAN_DAYS) {
            throw new TravelPlanValidationException("endDate",
                    "여행 기간은 최대 " + MAX_PLAN_DAYS + "일까지 설정할 수 있습니다.");
        }
        return (int) dayCount;
    }

    private ResponseStatusException saveFailed() {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "여행계획을 저장하지 못했습니다.");
    }
}
