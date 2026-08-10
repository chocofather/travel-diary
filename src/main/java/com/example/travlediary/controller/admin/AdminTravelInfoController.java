package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import com.example.travlediary.service.travelinfo.TravelInfoValidationException;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/admin/travel-info")
@RequiredArgsConstructor
public class AdminTravelInfoController {

    private static final String LIST_VIEW = "admin/travel-info/list";
    private static final String FORM_VIEW = "admin/travel-info/form";
    private static final String DETAIL_VIEW = "admin/travel-info/detail";
    private static final String REDIRECT_LIST = "redirect:/admin/travel-info";

    private final TravelInfoService travelInfoService;
    private final InfoCategoryService infoCategoryService;

    @GetMapping
    public String list(@RequestParam(required = false) TravelInfoScope scope,
                       @RequestParam(required = false) TravelInfoContentType contentType,
                       @RequestParam(required = false) Long categoryId,
                       Model model) {
        model.addAttribute("travelInfoList", travelInfoService.getAdminList(scope, contentType, categoryId));
        model.addAttribute("categories", infoCategoryService.getAll());
        model.addAttribute("scope", scope);
        model.addAttribute("contentType", contentType);
        model.addAttribute("categoryId", categoryId);
        return LIST_VIEW;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("travelInfo", travelInfoService.getAdminDetail(id));
        model.addAttribute("pageTitle", "여행정보 상세");
        return DETAIL_VIEW;
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        prepareFormModel(model, new TravelInfoForm(), null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("travelInfoForm") TravelInfoForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }

        try {
            travelInfoService.create(form, userDetails.getId());
        } catch (TravelInfoValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        prepareFormModel(model, travelInfoService.getForm(id), id);
        return FORM_VIEW;
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("travelInfoForm") TravelInfoForm form,
                         BindingResult bindingResult,
                         Model model) {
        travelInfoService.getById(id);
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }

        try {
            travelInfoService.update(id, form);
        } catch (TravelInfoValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        travelInfoService.delete(id);
        return REDIRECT_LIST;
    }

    private void rejectValidation(BindingResult bindingResult, TravelInfoValidationException exception) {
        if (exception.getField() == null || "periods".equals(exception.getField())) {
            bindingResult.reject("travelInfo.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "travelInfo.invalid", exception.getMessage());
    }

    private void prepareFormModel(Model model, TravelInfoForm form, Long id) {
        boolean editMode = id != null;
        model.addAttribute("travelInfoForm", form);
        model.addAttribute("categories", selectableCategories(form.getCategoryId()));
        model.addAttribute("editMode", editMode);
        model.addAttribute("travelInfoId", id);
        model.addAttribute("currentThumbnailUrl",
                editMode ? travelInfoService.getThumbnailUrl(id) : null);
        model.addAttribute("formAction", editMode
                ? "/admin/travel-info/edit/" + id
                : "/admin/travel-info");
        model.addAttribute("pageTitle", editMode ? "여행정보 수정" : "여행정보 등록");
        model.addAttribute("pageDescription", editMode
                ? "등록된 여행정보의 분류, 본문과 축제 기간을 수정합니다."
                : "국내·해외 여행정보와 축제 콘텐츠를 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "수정 저장" : "등록");
    }

    private List<InfoCategory> selectableCategories(Long selectedCategoryId) {
        return infoCategoryService.getAll().stream()
                .filter(category -> Boolean.TRUE.equals(category.getIsVisible())
                        || Objects.equals(category.getId(), selectedCategoryId))
                .toList();
    }
}
