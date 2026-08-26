package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanAlternativeForm;
import com.example.travlediary.dto.TravelPlanCreateForm;
import com.example.travlediary.dto.TravelPlanItemCreateForm;
import com.example.travlediary.dto.TravelPlanDetailDto;
import com.example.travlediary.dto.TravelPlanItemUpdateForm;
import com.example.travlediary.dto.TravelPlanMembersDto;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.service.travelplan.TravelPlanAccessNotice;
import com.example.travlediary.service.travelplan.TravelPlanConflictException;
import com.example.travlediary.service.travelplan.TravelPlanFinalReadService;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanInvitationService;
import com.example.travlediary.service.travelplan.TravelPlanService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;

@Controller
@RequestMapping("/travel-plans")
@RequiredArgsConstructor
public class TravelPlanController {

    private static final String FORM_ATTRIBUTE = "travelPlanCreateForm";
    private static final String LIST_VIEW = "travelplan/list";
    private static final String CREATE_VIEW = "travelplan/create";
    private static final String DETAIL_VIEW = "travelplan/detail";
    private static final String DAY_DETAIL_VIEW = "travelplan/day-detail";
    /** 완료된 여행. 읽기 전용이라 편집 화면과 다른 템플릿을 쓴다. */
    private static final String FINAL_DETAIL_VIEW = "travelplan/final-detail";
    /** 실시간 갱신이 가져가는 DAY 한 구역. 처음 그릴 때와 같은 fragment 다. */
    private static final String DAY_FRAGMENT_VIEW =
            "travelplan/fragments/schedule-day :: scheduleDay(plan=${plan}, days=${days},"
                    + " day=${day}, dayItems=${dayItems},"
                    + " alternativesByItemId=${alternativesByItemId}, dayOpen=${dayOpen})";
    /** 재연결 뒤 한 번에 따라잡을 때 쓰는 DAY 전체 묶음. */
    private static final String SCHEDULE_FRAGMENT_VIEW =
            "travelplan/fragments/schedule-day :: scheduleDays(plan=${plan}, days=${days},"
                    + " itemsByDayId=${itemsByDayId},"
                    + " alternativesByItemId=${alternativesByItemId})";
    /** 참여자 팝오버의 속. 처음 그릴 때와 실시간 갱신이 같은 fragment 를 쓴다. */
    private static final String MEMBERS_FRAGMENT_VIEW =
            "travelplan/fragments/members :: membersBody(planId=${planId},"
                    + " currentMember=${currentMember}, members=${members},"
                    + " pastMembers=${pastMembers}, memberCount=${memberCount},"
                    + " memberLimit=${memberLimit})";
    private static final String ITEM_FORM_ATTRIBUTE = "travelPlanItemCreateForm";

    /** 폼에 실제로 있는 필드만 필드 오류로 남길 수 있다. */
    private static final Set<String> FORM_FIELDS =
            Set.of("title", "startDate", "endDate", "displayName");

    private final TravelPlanService travelPlanService;
    private final TravelPlanInvitationService travelPlanInvitationService;
    /** 완료된 여행은 최종본에서만 읽는다. */
    private final TravelPlanFinalReadService travelPlanFinalReadService;

    // 함께 계획하기 목록
    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("travelPlans", travelPlanService.getActivePlans(userDetails.getId()));
        // 완료된 여행은 최종본에서만 읽는다. 원본 방을 다시 들여다보지 않는다.
        model.addAttribute("completedTravelPlans",
                travelPlanFinalReadService.getCompletedPlans(userDetails.getId()));
        return LIST_VIEW;
    }

    /**
     * 완료된 여행 상세. 읽기 전용이다.
     *
     * <p>완료 시점에 함께했던 사람만 볼 수 있고, 방장이든 아니든 같은 최종본을 본다.
     * 그 외에는 최종본의 존재 자체를 알리지 않는다.
     */
    @GetMapping("/{travelPlanId:\\d+}/final")
    public String finalDetail(@PathVariable Long travelPlanId,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("finalPlan",
                    travelPlanFinalReadService.getCompletedPlanDetail(
                            userDetails.getId(), travelPlanId));
        } catch (ResponseStatusException exception) {
            // 여기서도 흰 오류 화면을 보여 주지 않는다.
            redirectAttributes.addFlashAttribute("travelPlanNotice",
                    TravelPlanAccessNotice.NO_ACCESS.message());
            return "redirect:/travel-plans";
        }
        return FINAL_DETAIL_VIEW;
    }

    // 방 생성 폼
    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute(FORM_ATTRIBUTE)) {
            model.addAttribute(FORM_ATTRIBUTE, new TravelPlanCreateForm());
        }
        return CREATE_VIEW;
    }

    // 방 생성 처리
    @PostMapping
    public String create(@ModelAttribute(FORM_ATTRIBUTE) TravelPlanCreateForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        // 날짜 형식 오류처럼 바인딩 단계에서 걸린 값은 서비스로 넘기지 않는다.
        if (bindingResult.hasErrors()) {
            return CREATE_VIEW;
        }

        Long travelPlanId;
        try {
            travelPlanId = travelPlanService.createPlan(
                    userDetails.getId(),
                    form.getTitle(),
                    form.getStartDate(),
                    form.getEndDate(),
                    form.getDisplayName());
        } catch (TravelPlanValidationException exception) {
            rejectValidation(bindingResult, exception);
            return CREATE_VIEW;
        }

        redirectAttributes.addFlashAttribute("travelPlanMessage", "공동 여행계획이 만들어졌어요.");
        return "redirect:/travel-plans/" + travelPlanId;
    }

    // 방 기본 상세. 접근 권한은 Service 가 사용자 기준으로 확인한다.
    @GetMapping("/{travelPlanId:\\d+}")
    public String detail(@PathVariable Long travelPlanId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!model.containsAttribute(ITEM_FORM_ATTRIBUTE)) {
            model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        }
        try {
            model.addAttribute("travelPlan",
                    travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId));
        } catch (ResponseStatusException exception) {
            return redirectWithNotice(userDetails.getId(), travelPlanId, redirectAttributes);
        }
        addInviteState(model, userDetails.getId(), travelPlanId);
        return DETAIL_VIEW;
    }

    /**
     * 공동 편집방을 열지 못했다. 흰 오류 화면 대신 왜 못 여는지를 알리고 목록으로 보낸다.
     *
     * <p>무엇을 알릴지는 Service 가 정한다. 완료된 여행에 함께했던 사람에게만
     * 완료됐다고 알려 주고, 그 밖에는 방이 있는지조차 알리지 않는다.
     *
     * <p>완료된 여행 전용 화면이 생기면 여기 보낼 곳만 바꾸면 된다.
     */
    private String redirectWithNotice(Long userId, Long travelPlanId,
                                      RedirectAttributes redirectAttributes) {
        TravelPlanAccessNotice notice =
                travelPlanService.explainInaccessiblePlan(userId, travelPlanId);

        /*
          완료된 여행에 함께했던 사람이라면 볼 것이 있다.
          예전 편집방 주소로 들어와도 목록에서 다시 찾게 하지 않고 최종본으로 보낸다.
        */
        if (notice == TravelPlanAccessNotice.COMPLETED_PARTICIPANT) {
            return "redirect:/travel-plans/" + travelPlanId + "/final";
        }
        redirectAttributes.addFlashAttribute("travelPlanNotice", notice.message());
        return "redirect:/travel-plans";
    }

    /**
     * OWNER 의 초대 영역 상태.
     * 살아 있는 링크는 저장된 암호문을 풀어 지금 요청 기준 주소로 다시 만들어 준다.
     * 예전 방식으로 만들어져 풀 수 없는 링크는 주소 없이 "켜져 있음" 만 알린다.
     */
    private void addInviteState(Model model, Long userId, Long travelPlanId) {
        model.addAttribute("travelPlanInviteActive",
                travelPlanInvitationService.hasActiveInvitation(userId, travelPlanId));
        travelPlanInvitationService.findActiveInviteToken(userId, travelPlanId)
                .ifPresent(rawToken -> model.addAttribute("travelPlanInviteUrl",
                        ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/travel-plans/invitations/")
                                .path(rawToken)
                                .toUriString()));
    }

    /**
     * DAY 한 구역만 다시 그려 준다. 실시간 알림을 받은 화면이 이 조각만 갈아 끼운다.
     * 처음 그릴 때와 같은 fragment 를 쓰므로 화면이 갈라지지 않는다.
     * 접근 권한은 상세 화면과 똑같이 Service 가 확인한다(비참여자는 404).
     */
    @GetMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/fragment")
    public String dayFragment(@PathVariable Long travelPlanId,
                              @PathVariable Long dayId,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        TravelPlanDetailDto detail =
                travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId);
        TravelPlanDay day = detail.getDays().stream()
                .filter(candidate -> candidate.getId().equals(dayId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));

        model.addAttribute("plan", detail.getPlan());
        model.addAttribute("days", detail.getDays());
        model.addAttribute("day", day);
        model.addAttribute("dayItems", detail.getItemsByDayId().get(dayId));
        model.addAttribute("alternativesByItemId", detail.getAlternativesByItemId());
        // 실시간 갱신에서는 입력칸을 열어 두지 않는다.
        model.addAttribute("dayOpen", false);
        model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        return DAY_FRAGMENT_VIEW;
    }

    /**
     * DAY 전체를 한 번에 다시 그려 준다. 끊겼다 다시 붙은 화면이 밀린 변경을 한 번에 따라잡는다.
     * DAY 수만큼 요청이 나가지 않도록 평소 갱신과 달리 통째로 준다.
     */
    @GetMapping("/{travelPlanId:\\d+}/schedule/fragment")
    public String scheduleFragment(@PathVariable Long travelPlanId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {
        TravelPlanDetailDto detail =
                travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId);

        model.addAttribute("plan", detail.getPlan());
        model.addAttribute("days", detail.getDays());
        model.addAttribute("itemsByDayId", detail.getItemsByDayId());
        model.addAttribute("alternativesByItemId", detail.getAlternativesByItemId());
        model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        return SCHEDULE_FRAGMENT_VIEW;
    }

    /**
     * 참여자 명단만 다시 그려 준다. 명단이 바뀌었다는 실시간 알림을 받은 화면이 이 조각을 갈아 끼운다.
     *
     * <p>"참여자 N/8" 의 N 도 여기서 센 값이 함께 나간다.
     * 화면이 사람 수를 더하거나 빼지 않으므로 같은 알림을 두 번 받아도 숫자가 어긋나지 않는다.
     * 접근 권한은 상세 화면과 똑같이 Service 가 확인한다(비참여자는 404).
     */
    @GetMapping("/{travelPlanId:\\d+}/members/fragment")
    public String membersFragment(@PathVariable Long travelPlanId,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        TravelPlanMembersDto members =
                travelPlanService.getActivePlanMembers(userDetails.getId(), travelPlanId);

        model.addAttribute("planId", members.getPlan().getId());
        model.addAttribute("currentMember", members.getCurrentMember());
        model.addAttribute("members", members.getMembers());
        model.addAttribute("pastMembers", members.getPastMembers());
        model.addAttribute("memberCount", members.getMemberCount());
        model.addAttribute("memberLimit", members.getMemberLimit());
        return MEMBERS_FRAGMENT_VIEW;
    }

    // A 일정 수정 (방의 ACTIVE 멤버면 누구나)
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/update")
    public String updateItem(@PathVariable Long travelPlanId,
                             @PathVariable Long dayId,
                             @PathVariable Long itemId,
                             @ModelAttribute TravelPlanItemUpdateForm form,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             RedirectAttributes redirectAttributes) throws IOException {
        try {
            travelPlanService.updateItem(userDetails.getId(), travelPlanId, dayId, itemId,
                    form.getContent(), form.getVersion());
        } catch (TravelPlanValidationException | TravelPlanConflictException exception) {
            if (isAjax(request)) {
                // 입력을 날리지 않도록 화면이 그대로 이어 가고 사유만 알린다.
                return writeError(response, exception.getMessage());
            }
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
            return redirectToDay(travelPlanId, dayId);
        }
        // 화면이 스스로 갱신하므로 새로고침 없이 끝난다.
        return isAjax(request) ? noContent(response) : redirectToDay(travelPlanId, dayId);
    }

    // A 일정 삭제 (방의 ACTIVE 멤버면 누구나)
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/delete")
    public String deleteItem(@PathVariable Long travelPlanId,
                             @PathVariable Long dayId,
                             @PathVariable Long itemId,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        travelPlanService.deleteItem(userDetails.getId(), travelPlanId, dayId, itemId);
        return redirectToDay(travelPlanId, dayId);
    }

    // A + 대안 전체 삭제 (방의 ACTIVE 멤버면 누구나)
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/delete-group")
    public String deleteItemGroup(@PathVariable Long travelPlanId,
                                  @PathVariable Long dayId,
                                  @PathVariable Long itemId,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        travelPlanService.deleteItemGroup(userDetails.getId(), travelPlanId, dayId, itemId);
        return redirectToDay(travelPlanId, dayId);
    }

    // 대안(B/C) 추가
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/alternatives")
    public String addAlternative(@PathVariable Long travelPlanId,
                                 @PathVariable Long dayId,
                                 @PathVariable Long itemId,
                                 @ModelAttribute TravelPlanAlternativeForm form,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) throws IOException {
        try {
            travelPlanService.addAlternative(userDetails.getId(), travelPlanId, dayId, itemId,
                    form.getConditionLabel(), form.getContent());
        } catch (TravelPlanValidationException exception) {
            if (isAjax(request)) {
                return writeError(response, exception.getMessage());
            }
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
            return redirectToDay(travelPlanId, dayId);
        }
        return isAjax(request) ? noContent(response) : redirectToDay(travelPlanId, dayId);
    }

    // 대안(B/C) 수정
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}"
            + "/alternatives/{alternativeId:\\d+}/update")
    public String updateAlternative(@PathVariable Long travelPlanId,
                                    @PathVariable Long dayId,
                                    @PathVariable Long itemId,
                                    @PathVariable Long alternativeId,
                                    @ModelAttribute TravelPlanAlternativeForm form,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    RedirectAttributes redirectAttributes) throws IOException {
        try {
            travelPlanService.updateAlternative(userDetails.getId(), travelPlanId, dayId, itemId,
                    alternativeId, form.getConditionLabel(), form.getContent(), form.getVersion());
        } catch (TravelPlanValidationException | TravelPlanConflictException exception) {
            if (isAjax(request)) {
                return writeError(response, exception.getMessage());
            }
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
            return redirectToDay(travelPlanId, dayId);
        }
        return isAjax(request) ? noContent(response) : redirectToDay(travelPlanId, dayId);
    }

    // 대안(B/C) 삭제
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}"
            + "/alternatives/{alternativeId:\\d+}/delete")
    public String deleteAlternative(@PathVariable Long travelPlanId,
                                    @PathVariable Long dayId,
                                    @PathVariable Long itemId,
                                    @PathVariable Long alternativeId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        travelPlanService.deleteAlternative(
                userDetails.getId(), travelPlanId, dayId, itemId, alternativeId);
        return redirectToDay(travelPlanId, dayId);
    }

    // 같은 DAY 안에서 한 칸 위로
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/move-up")
    public String moveItemUp(@PathVariable Long travelPlanId,
                             @PathVariable Long dayId,
                             @PathVariable Long itemId,
                             @RequestParam Integer version,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            travelPlanService.moveItemUp(userDetails.getId(), travelPlanId, dayId, itemId, version);
        } catch (TravelPlanValidationException | TravelPlanConflictException exception) {
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
        }
        return redirectToDay(travelPlanId, dayId);
    }

    // 같은 DAY 안에서 한 칸 아래로
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/move-down")
    public String moveItemDown(@PathVariable Long travelPlanId,
                               @PathVariable Long dayId,
                               @PathVariable Long itemId,
                               @RequestParam Integer version,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            travelPlanService.moveItemDown(
                    userDetails.getId(), travelPlanId, dayId, itemId, version);
        } catch (TravelPlanValidationException | TravelPlanConflictException exception) {
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
        }
        return redirectToDay(travelPlanId, dayId);
    }

    // 다른 DAY 의 마지막으로 이동
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items/{itemId:\\d+}/move")
    public String moveItemToDay(@PathVariable Long travelPlanId,
                                @PathVariable Long dayId,
                                @PathVariable Long itemId,
                                @RequestParam Long targetDayId,
                                @RequestParam Integer version,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        try {
            travelPlanService.moveItemToDay(
                    userDetails.getId(), travelPlanId, dayId, itemId, targetDayId, version);
        } catch (TravelPlanValidationException | TravelPlanConflictException exception) {
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
            return redirectToDay(travelPlanId, dayId);
        }
        // 옮겨 간 DAY 자리에서 결과를 보여 준다.
        return redirectToDay(travelPlanId, targetDayId);
    }

    private String redirectToDay(Long travelPlanId, Long dayId) {
        return "redirect:/travel-plans/" + travelPlanId + "#day-" + dayId;
    }

    // DAY 편집 화면
    @GetMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}")
    public String dayDetail(@PathVariable Long travelPlanId,
                            @PathVariable Long dayId,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (!model.containsAttribute(ITEM_FORM_ATTRIBUTE)) {
            model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        }
        try {
            model.addAttribute("travelPlanDay",
                    travelPlanService.getActiveDayDetail(userDetails.getId(), travelPlanId, dayId));
        } catch (ResponseStatusException exception) {
            // DAY 화면도 같은 안내를 거쳐 목록으로 보낸다.
            return redirectWithNotice(userDetails.getId(), travelPlanId, redirectAttributes);
        }
        return DAY_DETAIL_VIEW;
    }

    // A 일정 추가
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items")
    public String addItem(@PathVariable Long travelPlanId,
                          @PathVariable Long dayId,
                          @ModelAttribute(ITEM_FORM_ATTRIBUTE) TravelPlanItemCreateForm form,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          Model model) throws IOException {
        try {
            travelPlanService.addItem(userDetails.getId(), travelPlanId, dayId, form.getContent());
        } catch (TravelPlanValidationException exception) {
            if (isAjax(request)) {
                return writeError(response, exception.getMessage());
            }
            bindingResult.rejectValue("content", "travelPlan.invalid", exception.getMessage());
            // 편집 화면을 그대로 다시 그리고, 문제가 난 DAY 의 입력칸만 열어 둔다.
            model.addAttribute("travelPlan",
                    travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId));
            addInviteState(model, userDetails.getId(), travelPlanId);
            model.addAttribute("openDayId", dayId);
            return DETAIL_VIEW;
        }
        // 화면이 스스로 갱신하므로 새로고침 없이 끝난다.
        // 스크립트가 없는 경우에는 지금까지처럼 그 DAY 자리로 돌아온다.
        return isAjax(request) ? noContent(response) : redirectToDay(travelPlanId, dayId);
    }

    /** 화면이 직접 보낸 저장인지. 그렇다면 redirect 대신 결과만 돌려준다. */
    private boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private String noContent(HttpServletResponse response) {
        response.setStatus(HttpStatus.NO_CONTENT.value());
        return null;
    }

    /** 500 HTML 이 화면에 그대로 박히지 않도록 사유만 짧게 돌려준다. */
    private String writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message == null ? "" : message);
        return null;
    }

    private void rejectValidation(BindingResult bindingResult,
                                  TravelPlanValidationException exception) {
        String field = exception.getField();
        if (field == null || !FORM_FIELDS.contains(field)) {
            bindingResult.reject("travelPlan.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(field, "travelPlan.invalid", exception.getMessage());
    }
}
