package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanCreateForm;
import com.example.travlediary.dto.TravelPlanItemCreateForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private static final String ITEM_FORM_ATTRIBUTE = "travelPlanItemCreateForm";

    /** 폼에 실제로 있는 필드만 필드 오류로 남길 수 있다. */
    private static final Set<String> FORM_FIELDS =
            Set.of("title", "startDate", "endDate", "displayName");

    private final TravelPlanService travelPlanService;

    // 함께 계획하기 목록
    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("travelPlans", travelPlanService.getActivePlans(userDetails.getId()));
        return LIST_VIEW;
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
                         Model model) {
        if (!model.containsAttribute(ITEM_FORM_ATTRIBUTE)) {
            model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        }
        model.addAttribute("travelPlan",
                travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId));
        return DETAIL_VIEW;
    }

    // DAY 편집 화면
    @GetMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}")
    public String dayDetail(@PathVariable Long travelPlanId,
                            @PathVariable Long dayId,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        if (!model.containsAttribute(ITEM_FORM_ATTRIBUTE)) {
            model.addAttribute(ITEM_FORM_ATTRIBUTE, new TravelPlanItemCreateForm());
        }
        model.addAttribute("travelPlanDay",
                travelPlanService.getActiveDayDetail(userDetails.getId(), travelPlanId, dayId));
        return DAY_DETAIL_VIEW;
    }

    // A 일정 추가
    @PostMapping("/{travelPlanId:\\d+}/days/{dayId:\\d+}/items")
    public String addItem(@PathVariable Long travelPlanId,
                          @PathVariable Long dayId,
                          @ModelAttribute(ITEM_FORM_ATTRIBUTE) TravelPlanItemCreateForm form,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        try {
            travelPlanService.addItem(userDetails.getId(), travelPlanId, dayId, form.getContent());
        } catch (TravelPlanValidationException exception) {
            bindingResult.rejectValue("content", "travelPlan.invalid", exception.getMessage());
            // 편집 화면을 그대로 다시 그리고, 문제가 난 DAY 의 입력칸만 열어 둔다.
            model.addAttribute("travelPlan",
                    travelPlanService.getActivePlanDetail(userDetails.getId(), travelPlanId));
            model.addAttribute("openDayId", dayId);
            return DETAIL_VIEW;
        }
        // 메인 편집 화면의 해당 DAY 자리로 돌아온다.
        return "redirect:/travel-plans/" + travelPlanId + "#day-" + dayId;
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
