package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.service.category.DuplicateInfoCategoryNameException;
import com.example.travlediary.service.category.InfoCategoryInUseException;
import com.example.travlediary.service.category.InfoCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/info-categories")
@RequiredArgsConstructor
public class AdminInfoCategoryController {

    private static final String FORM_VIEW = "admin/info-categories/form";
    private static final String REDIRECT_LIST = "redirect:/admin/info-categories";

    private final InfoCategoryService infoCategoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", infoCategoryService.getAll());
        return "admin/info-categories/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        prepareFormModel(model, new InfoCategoryForm(), null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("infoCategoryForm") InfoCategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        validateForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }

        try {
            infoCategoryService.create(form);
        } catch (DuplicateInfoCategoryNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        InfoCategory category = infoCategoryService.getById(id);
        prepareFormModel(model, InfoCategoryForm.from(category), id);
        return FORM_VIEW;
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("infoCategoryForm") InfoCategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        infoCategoryService.getById(id);
        validateForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }

        try {
            infoCategoryService.update(id, form);
        } catch (DuplicateInfoCategoryNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            infoCategoryService.delete(id);
        } catch (InfoCategoryInUseException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return REDIRECT_LIST;
    }

    private void validateForm(InfoCategoryForm form, BindingResult bindingResult) {
        String name = form.getName() == null ? null : form.getName().strip();
        form.setName(name);

        if (name == null || name.isBlank()) {
            bindingResult.rejectValue("name", "required", "카테고리명을 입력해 주세요.");
        } else if (name.length() > 100) {
            bindingResult.rejectValue("name", "maxLength", "카테고리명은 100자 이하로 입력해 주세요.");
        }

        if (form.getDisplayOrder() == null) {
            bindingResult.rejectValue("displayOrder", "required", "표시 순서를 입력해 주세요.");
        } else if (form.getDisplayOrder() < 1) {
            bindingResult.rejectValue("displayOrder", "min", "표시 순서는 1 이상이어야 합니다.");
        }

        if (form.getIsVisible() == null) {
            bindingResult.rejectValue("isVisible", "required", "노출 여부를 선택해 주세요.");
        }
    }

    private void prepareFormModel(Model model, InfoCategoryForm form, Long categoryId) {
        boolean editMode = categoryId != null;
        model.addAttribute("infoCategoryForm", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("formAction", editMode
                ? "/admin/info-categories/edit/" + categoryId
                : "/admin/info-categories");
        model.addAttribute("pageTitle", editMode ? "정보 카테고리 수정" : "정보 카테고리 등록");
        model.addAttribute("pageDescription", editMode
                ? "여행정보 주제 카테고리의 이름과 노출 설정을 수정합니다."
                : "여행정보에서 사용할 새 주제 카테고리를 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "수정" : "등록");
    }
}
