package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.FestivalCreateForm;
import com.example.travlediary.dto.FestivalEditData;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.FestivalAdminService;
import com.example.travlediary.service.travelinfo.FestivalRegistrationService;
import com.example.travlediary.service.travelinfo.FestivalRegistrationResult;
import com.example.travlediary.service.travelinfo.FestivalValidationException;
import com.example.travlediary.service.travelinfo.TravelInfoService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalController {

    private static final String LIST_VIEW = "admin/festivals/list";
    private static final String CREATE_VIEW = "admin/festivals/form";

    private final TravelInfoService travelInfoService;
    private final InfoCategoryService infoCategoryService;
    private final FestivalRegistrationService festivalRegistrationService;
    private final FestivalAdminService festivalAdminService;

    @GetMapping
    public String list(@RequestParam(required = false) TravelInfoScope scope,
                       @RequestParam(required = false) Long categoryId,
                       Model model) {
        model.addAttribute("festivalList", travelInfoService.getAdminList(
                scope, TravelInfoContentType.FESTIVAL, categoryId));
        model.addAttribute("categories", categoriesByContentType(TravelInfoContentType.FESTIVAL));
        model.addAttribute("scope", scope);
        model.addAttribute("categoryId", categoryId);
        return LIST_VIEW;
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        FestivalCreateForm festivalForm = new FestivalCreateForm();
        prepareCreateForm(model, festivalForm);
        return CREATE_VIEW;
    }

    @PostMapping("/create")
    public String create(@ModelAttribute("festivalForm") FestivalCreateForm festivalForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                FestivalRegistrationResult result = festivalRegistrationService.create(festivalForm, currentUser.getId());
                redirectAttributes.addFlashAttribute("festivalMessage", "축제·행사가 등록되었습니다.");
                if (result.imageWarning() != null && !result.imageWarning().isBlank()) {
                    redirectAttributes.addFlashAttribute("festivalImageWarning", result.imageWarning());
                }
            } catch (FestivalValidationException exception) {
                if (exception.getField() == null) {
                    bindingResult.reject("festival.create", exception.getMessage());
                } else {
                    bindingResult.rejectValue(exception.getField(), "festival.create", exception.getMessage());
                }
            }
        }
        if (bindingResult.hasErrors()) {
            prepareCreateForm(model, festivalForm);
            return CREATE_VIEW;
        }

        return "redirect:/admin/festivals";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FestivalEditData editData = festivalAdminService.getEditData(id);
        prepareEditForm(model, editData.form(), id, editData.images());
        return CREATE_VIEW;
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute("festivalForm") FestivalEditForm festivalForm,
                       BindingResult bindingResult,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                festivalAdminService.update(id, festivalForm);
            } catch (FestivalValidationException exception) {
                rejectValidation(bindingResult, exception, "festival.edit");
            }
        }
        if (bindingResult.hasErrors()) {
            FestivalEditData persisted = festivalAdminService.getEditData(id);
            prepareEditForm(model, festivalForm, id, persisted.images());
            return CREATE_VIEW;
        }

        redirectAttributes.addFlashAttribute("festivalMessage", "축제·행사가 수정되었습니다.");
        return "redirect:/admin/festivals";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        festivalAdminService.delete(id);
        redirectAttributes.addFlashAttribute("festivalMessage", "축제·행사가 삭제되었습니다.");
        return "redirect:/admin/festivals";
    }

    private void prepareCreateForm(Model model, FestivalCreateForm festivalForm) {
        model.addAttribute("festivalForm", festivalForm);
        model.addAttribute("categories", categoriesByContentType(TravelInfoContentType.FESTIVAL));
        model.addAttribute("scope", festivalForm.getScope());
        model.addAttribute("editMode", false);
        model.addAttribute("formAction", "/admin/festivals/create");
    }

    private void prepareEditForm(Model model,
                                 FestivalEditForm festivalForm,
                                 Long id,
                                 List<InfoImage> images) {
        model.addAttribute("festivalForm", festivalForm);
        model.addAttribute("categories", categoriesByContentType(TravelInfoContentType.FESTIVAL));
        model.addAttribute("scope", festivalForm.getScope());
        model.addAttribute("editMode", true);
        model.addAttribute("festivalId", id);
        model.addAttribute("festivalImages", images);
        model.addAttribute("formAction", "/admin/festivals/" + id + "/edit");
    }

    private void rejectValidation(BindingResult bindingResult,
                                  FestivalValidationException exception,
                                  String errorCode) {
        if (exception.getField() == null) {
            bindingResult.reject(errorCode, exception.getMessage());
        } else {
            bindingResult.rejectValue(exception.getField(), errorCode, exception.getMessage());
        }
    }

    private List<InfoCategory> categoriesByContentType(TravelInfoContentType contentType) {
        return infoCategoryService.getAll().stream()
                .filter(category -> category.getContentType() == contentType)
                .toList();
    }
}
