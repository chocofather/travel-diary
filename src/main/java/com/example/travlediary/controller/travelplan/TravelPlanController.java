package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanCreateForm;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequestMapping("/travel-plans")
@RequiredArgsConstructor
public class TravelPlanController {

    private static final String FORM_ATTRIBUTE = "travelPlanCreateForm";
    private static final String CREATE_VIEW = "travelplan/create";
    private static final String REDIRECT_NEW = "redirect:/travel-plans/new";

    /** 폼에 실제로 있는 필드만 필드 오류로 남길 수 있다. */
    private static final Set<String> FORM_FIELDS =
            Set.of("title", "startDate", "endDate", "displayName");

    private final TravelPlanService travelPlanService;

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

        // 아직 목록/상세 화면이 없어 생성 폼으로 되돌린다. id 는 URL 에 노출하지 않는다.
        redirectAttributes.addFlashAttribute("travelPlanMessage", "공동 여행계획이 만들어졌어요.");
        redirectAttributes.addFlashAttribute("createdTravelPlanId", travelPlanId);
        return REDIRECT_NEW;
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
