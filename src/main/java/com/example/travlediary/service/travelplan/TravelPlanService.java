package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanDayDetailDto;
import com.example.travlediary.dto.TravelPlanDetailDto;
import com.example.travlediary.dto.TravelPlanListItemDto;
import com.example.travlediary.dto.TravelPlanMemberDto;
import com.example.travlediary.dto.TravelPlanPastMemberDto;
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
    /** 시작일과 종료일을 포함한 최대 여행 일수. DB 제약이 아니라 서비스 정책이다. */
    private static final int MAX_PLAN_DAYS = 90;
    /** travel_plan_item_alternatives.condition_label 은 varchar(100) */
    private static final int MAX_CONDITION_LABEL_LENGTH = 100;
    /** A 일정 하나가 가질 수 있는 대안 수. chk_travel_plan_item_alternatives_order 와 같은 값이다. */
    private static final int MAX_ALTERNATIVES = 2;
    /** 대안 중 A 자리로 올라가는 것은 항상 B(1번)다. */
    private static final int FIRST_ALTERNATIVE_ORDER = 1;
    private static final int SECOND_ALTERNATIVE_ORDER = 2;

    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanItemMapper travelPlanItemMapper;
    private final TravelPlanAlternativeMapper travelPlanAlternativeMapper;

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

        // 대안도 같은 이유로 일정 수만큼 조회하지 않고 방 단위로 한 번에 읽는다.
        List<TravelPlanItemAlternative> alternatives =
                travelPlanAlternativeMapper.findByPlanId(travelPlanId);
        Map<Long, List<TravelPlanItemAlternative>> alternativesByItemId = alternatives == null
                ? Map.of()
                : alternatives.stream().collect(Collectors.groupingBy(
                        TravelPlanItemAlternative::getTravelPlanItemId,
                        LinkedHashMap::new, Collectors.toList()));

        return new TravelPlanDetailDto(
                access.plan(), access.member(), days == null ? List.of() : days,
                itemsByDayId, alternativesByItemId,
                activeMembers(travelPlanId, access.member()),
                pastMembers(travelPlanId, access.member()),
                TravelPlanInvitationService.MAX_MEMBERS);
    }

    /**
     * 내보내진 사람들. OWNER 만 관리할 수 있으므로 OWNER 가 아니면 조회조차 하지 않는다.
     * 스스로 나간 사람은 본인이 초대 링크로 돌아올 수 있어 여기 담지 않는다.
     */
    private List<TravelPlanPastMemberDto> pastMembers(Long travelPlanId,
                                                      TravelPlanMember currentMember) {
        if (currentMember == null || currentMember.getRole() != TravelPlanRole.OWNER) {
            return List.of();
        }
        List<TravelPlanMember> removed = travelPlanMapper.findMembersByPlanAndStatus(
                travelPlanId, TravelPlanMemberStatus.REMOVED.name());
        if (removed == null) {
            return List.of();
        }
        return removed.stream()
                .map(member -> new TravelPlanPastMemberDto(
                        member.getId(),
                        member.getDisplayName(),
                        Boolean.TRUE.equals(member.getRejoinAllowed())))
                .toList();
    }

    /**
     * 화면에 내보낼 참여자 목록.
     * 모델을 그대로 넘기지 않고 표시 이름과 역할만 옮겨 담아
     * user_id 나 계정 정보가 view 까지 가지 않게 한다.
     */
    private List<TravelPlanMemberDto> activeMembers(Long travelPlanId,
                                                    TravelPlanMember currentMember) {
        List<TravelPlanMember> members = travelPlanMapper.findActiveMembersByPlanId(
                travelPlanId, TravelPlanMemberStatus.ACTIVE.name());
        if (members == null) {
            return List.of();
        }
        Long currentMemberId = currentMember == null ? null : currentMember.getId();
        return members.stream()
                .map(member -> new TravelPlanMemberDto(
                        member.getId(),
                        member.getDisplayName(),
                        member.getRole(),
                        // 사용자 식별은 방 참여 id 로만 한다. user_id 는 읽지도 않는다.
                        member.getId() != null && member.getId().equals(currentMemberId)))
                .toList();
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
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * A 일정 내용 수정.
     * 방의 ACTIVE 멤버라면 자기가 쓰지 않은 일정도 고칠 수 있다(공동 편집).
     * version 이 그 사이 바뀌었으면 덮어쓰지 않고 충돌로 알린다.
     */
    @Transactional
    public void updateItem(Long userId, Long travelPlanId, Long dayId, Long itemId,
                           String content, Integer version) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);

        String normalizedContent = requiredContent(content);
        if (version == null) {
            throw new TravelPlanConflictException();
        }

        if (travelPlanItemMapper.updateContent(itemId, dayId, normalizedContent, version) != 1) {
            throw new TravelPlanConflictException();
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * A 일정만 삭제한다.
     * 대안이 없으면 그 줄을 지우고 그 DAY 의 순서만 1..N 으로 다시 매긴다.
     * 대안이 있으면 줄을 없애지 않고 B 를 A 자리로 끌어올린다(C 가 있으면 C 가 B 가 된다).
     */
    @Transactional
    public void deleteItem(Long userId, Long travelPlanId, Long dayId, Long itemId) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);

        TravelPlanItemAlternative promoted = travelPlanAlternativeMapper.findByItemIdAndOrder(
                itemId, FIRST_ALTERNATIVE_ORDER);
        if (promoted != null) {
            promoteAlternativeToItem(travelPlanId, dayId, itemId, promoted);
            return;
        }

        if (travelPlanItemMapper.deleteByIdAndDayId(itemId, dayId) != 1) {
            throw planNotFound();
        }
        travelPlanItemMapper.resequenceDisplayOrder(dayId);
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * A 일정과 거기 붙은 대안을 통째로 지운다.
     * 대안은 FK CASCADE 로 함께 사라지므로 여기서 따로 지우지 않는다.
     */
    @Transactional
    public void deleteItemGroup(Long userId, Long travelPlanId, Long dayId, Long itemId) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);

        if (travelPlanItemMapper.deleteByIdAndDayId(itemId, dayId) != 1) {
            throw planNotFound();
        }
        travelPlanItemMapper.resequenceDisplayOrder(dayId);
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * B 를 A 자리로 올린다.
     * parent row 를 지우지 않고 내용만 갈아 끼우므로 item id 와 display_order 는 그대로다.
     * condition_label 은 A 에 없는 개념이라 버리고, 작성자는 대안 작성자로 바뀐다.
     */
    private void promoteAlternativeToItem(Long travelPlanId, Long dayId, Long itemId,
                                          TravelPlanItemAlternative promoted) {
        if (travelPlanItemMapper.promoteAlternativeContent(itemId, dayId, promoted.getContent(),
                promoted.getTag(), promoted.getCreatedByMemberId()) != 1) {
            throw planNotFound();
        }
        if (travelPlanAlternativeMapper.deleteByIdAndItemId(promoted.getId(), itemId) != 1) {
            throw planNotFound();
        }
        // 비어 버린 B 자리로 C 를 당겨 온다.
        shiftSecondAlternativeUp(itemId);
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * DAY 안의 A 일정에 대안을 붙인다.
     * 첫 대안은 B(1번), 두 번째는 C(2번)이고 세 번째는 받지 않는다.
     * 작성자는 요청 값을 믿지 않고 현재 사용자의 방 참여 정보에서 가져온다.
     */
    @Transactional
    public void addAlternative(Long userId, Long travelPlanId, Long dayId, Long itemId,
                               String conditionLabel, String content) {
        PlanAccess access = requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);

        String normalizedContent = requiredContent(content);
        String normalizedCondition = optionalConditionLabel(conditionLabel);

        // 화면에서 버튼을 숨기는 것과 별개로 서버에서도 개수를 확인한다.
        int existing = travelPlanAlternativeMapper.countByItemId(itemId);
        if (existing >= MAX_ALTERNATIVES) {
            throw new TravelPlanValidationException("content",
                    "대안은 일정마다 " + MAX_ALTERNATIVES + "개까지 추가할 수 있습니다.");
        }

        TravelPlanItemAlternative alternative = new TravelPlanItemAlternative();
        alternative.setTravelPlanItemId(itemId);
        alternative.setAlternativeOrder(existing + 1);
        alternative.setConditionLabel(normalizedCondition);
        alternative.setContent(normalizedContent);
        // 태그 UI 는 아직 없다.
        alternative.setTag(null);
        alternative.setCreatedByMemberId(access.member().getId());

        if (travelPlanAlternativeMapper.insertAlternative(alternative) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "대안을 저장하지 못했습니다.");
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * 대안 내용 수정.
     * A 일정과 마찬가지로 방의 ACTIVE 멤버라면 자기가 쓰지 않은 대안도 고칠 수 있다.
     * version 이 그 사이 바뀌었으면 덮어쓰지 않고 충돌로 알린다.
     */
    @Transactional
    public void updateAlternative(Long userId, Long travelPlanId, Long dayId, Long itemId,
                                  Long alternativeId, String conditionLabel, String content,
                                  Integer version) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);
        requireAlternativeOfItem(itemId, alternativeId);

        String normalizedContent = requiredContent(content);
        String normalizedCondition = optionalConditionLabel(conditionLabel);
        if (version == null) {
            throw new TravelPlanConflictException();
        }

        if (travelPlanAlternativeMapper.updateWithVersion(
                alternativeId, itemId, normalizedCondition, normalizedContent, version) != 1) {
            throw new TravelPlanConflictException();
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * 대안 삭제.
     * C 를 지우면 그냥 사라지고, B 를 지우면 남아 있던 C 가 B 자리로 올라온다.
     */
    @Transactional
    public void deleteAlternative(Long userId, Long travelPlanId, Long dayId, Long itemId,
                                  Long alternativeId) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        requireItemOfDay(dayId, itemId);
        TravelPlanItemAlternative alternative = requireAlternativeOfItem(itemId, alternativeId);

        if (travelPlanAlternativeMapper.deleteByIdAndItemId(alternativeId, itemId) != 1) {
            throw planNotFound();
        }
        if (Integer.valueOf(FIRST_ALTERNATIVE_ORDER).equals(alternative.getAlternativeOrder())) {
            shiftSecondAlternativeUp(itemId);
        }
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /** B 자리가 비었을 때 C 를 그 자리로 당긴다. 내용/조건/작성자는 그대로 둔다. */
    private void shiftSecondAlternativeUp(Long itemId) {
        TravelPlanItemAlternative second = travelPlanAlternativeMapper.findByItemIdAndOrder(
                itemId, SECOND_ALTERNATIVE_ORDER);
        if (second == null) {
            return;
        }
        travelPlanAlternativeMapper.updateOrderByIdAndItemId(
                second.getId(), itemId, FIRST_ALTERNATIVE_ORDER);
    }

    /** 다른 일정의 alternativeId 를 섞어 넣어도 통과하지 못하게 한다. */
    private TravelPlanItemAlternative requireAlternativeOfItem(Long itemId, Long alternativeId) {
        TravelPlanItemAlternative alternative = alternativeId == null
                ? null : travelPlanAlternativeMapper.findByIdAndItemId(alternativeId, itemId);
        if (alternative == null) {
            throw planNotFound();
        }
        return alternative;
    }

    /** 조건은 선택 입력이다. 빈 문자열은 NULL 로 저장한다. */
    private String optionalConditionLabel(String value) {
        String label = value == null ? "" : value.trim();
        if (label.isEmpty()) {
            return null;
        }
        if (label.length() > MAX_CONDITION_LABEL_LENGTH) {
            throw new TravelPlanValidationException("conditionLabel",
                    "조건은 " + MAX_CONDITION_LABEL_LENGTH + "자 이하로 입력해 주세요.");
        }
        return label;
    }

    /** 같은 DAY 안에서 바로 위 일정과 자리를 바꾼다. */
    @Transactional
    public void moveItemUp(Long userId, Long travelPlanId, Long dayId, Long itemId,
                           Integer version) {
        TravelPlanItem item = requireMovableItem(userId, travelPlanId, dayId, itemId, version);
        TravelPlanItem neighbour =
                travelPlanItemMapper.findPreviousItem(dayId, item.getDisplayOrder());
        if (neighbour == null) {
            throw new TravelPlanValidationException("itemId", "이미 첫 번째 일정입니다.");
        }
        swap(travelPlanId, dayId, item, neighbour, version);
    }

    /** 같은 DAY 안에서 바로 아래 일정과 자리를 바꾼다. */
    @Transactional
    public void moveItemDown(Long userId, Long travelPlanId, Long dayId, Long itemId,
                             Integer version) {
        TravelPlanItem item = requireMovableItem(userId, travelPlanId, dayId, itemId, version);
        TravelPlanItem neighbour =
                travelPlanItemMapper.findNextItem(dayId, item.getDisplayOrder());
        if (neighbour == null) {
            throw new TravelPlanValidationException("itemId", "이미 마지막 일정입니다.");
        }
        swap(travelPlanId, dayId, item, neighbour, version);
    }

    /**
     * 일정을 다른 DAY 의 마지막으로 옮긴다.
     * content / tag / created_by_member_id 는 그대로 두고 소속 DAY 와 순서만 바꾼다.
     */
    @Transactional
    public void moveItemToDay(Long userId, Long travelPlanId, Long sourceDayId, Long itemId,
                              Long targetDayId, Integer version) {
        requireMovableItem(userId, travelPlanId, sourceDayId, itemId, version);
        if (targetDayId == null || targetDayId.equals(sourceDayId)) {
            throw new TravelPlanValidationException("targetDayId", "옮길 DAY 를 선택해 주세요.");
        }
        // 같은 방의 DAY 인지 확인한다. 다른 방의 dayId 는 여기서 걸린다.
        requireDayOfPlan(travelPlanId, targetDayId);

        int lastOrder = travelPlanItemMapper.findMaxDisplayOrder(targetDayId);
        if (travelPlanItemMapper.moveToDayWithVersion(
                itemId, sourceDayId, targetDayId, lastOrder + 1, version) != 1) {
            throw new TravelPlanConflictException();
        }
        // 빠져나온 DAY 의 번호를 다시 이어 준다.
        travelPlanItemMapper.resequenceDisplayOrder(sourceDayId);
        travelPlanItemMapper.resequenceDisplayOrder(targetDayId);
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /**
     * 두 일정의 순서를 맞바꾼다.
     * 지금은 (day, order) UNIQUE 가 없지만, 나중에 생겨도 안전하도록
     * 이웃을 임시 자리로 잠깐 비켜 둔 뒤 교환한다.
     * 임시 자리는 DAY 의 마지막 순서 다음 칸이다.
     * display_order 는 중간 상태에서도 1 이상이어야 한다(chk_travel_plan_items_display_order).
     */
    private void swap(Long travelPlanId, Long dayId, TravelPlanItem item,
                      TravelPlanItem neighbour, Integer version) {
        int itemOrder = item.getDisplayOrder();
        int neighbourOrder = neighbour.getDisplayOrder();
        int temporaryOrder = travelPlanItemMapper.findMaxDisplayOrder(dayId) + 1;

        travelPlanItemMapper.updateDisplayOrderById(neighbour.getId(), dayId, temporaryOrder);
        if (travelPlanItemMapper.updateDisplayOrderWithVersion(
                item.getId(), dayId, neighbourOrder, version) != 1) {
            throw new TravelPlanConflictException();
        }
        travelPlanItemMapper.updateDisplayOrderById(neighbour.getId(), dayId, itemOrder);

        // 임시 자리를 쓴 뒤라도 1..N 으로 이어지게 맞춘다.
        travelPlanItemMapper.resequenceDisplayOrder(dayId);
        travelPlanMapper.touchLastActivity(travelPlanId);
    }

    /** 이동 계열이 공통으로 하는 확인. 방 -> DAY -> 일정 -> version 순으로 본다. */
    private TravelPlanItem requireMovableItem(Long userId, Long travelPlanId, Long dayId,
                                              Long itemId, Integer version) {
        requireActiveAccess(userId, travelPlanId);
        requireDayOfPlan(travelPlanId, dayId);
        TravelPlanItem item = requireItemOfDay(dayId, itemId);
        if (version == null) {
            throw new TravelPlanConflictException();
        }
        return item;
    }

    /** 다른 DAY 의 itemId 를 섞어 넣어도 통과하지 못하게 한다. */
    private TravelPlanItem requireItemOfDay(Long dayId, Long itemId) {
        TravelPlanItem item = itemId == null
                ? null : travelPlanItemMapper.findByIdAndDayId(itemId, dayId);
        if (item == null) {
            throw planNotFound();
        }
        return item;
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
        // 초대로 들어오는 MEMBER 와 같은 규칙을 쓴다.
        String normalizedDisplayName = TravelPlanDisplayName.normalize(displayName);
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
