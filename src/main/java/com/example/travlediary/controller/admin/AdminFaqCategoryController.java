package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.FaqCategoryForm;
import com.example.travlediary.service.faq.FaqCategoryInUseException;
import com.example.travlediary.service.faq.FaqCategoryService;
import com.example.travlediary.service.faq.FaqValidationException;
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
@RequestMapping("/admin/faq-categories")
@RequiredArgsConstructor
public class AdminFaqCategoryController {

    private static final String LIST_VIEW = "admin/faq-categories/list";
    private static final String FORM_VIEW = "admin/faq-categories/form";
    private static final String REDIRECT_LIST = "redirect:/admin/faq-categories";

    private final FaqCategoryService faqCategoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", faqCategoryService.getAdminList());
        model.addAttribute("pageTitle", "FAQ 카테고리 관리");
        return LIST_VIEW;
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        prepareFormModel(model, new FaqCategoryForm(), null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("faqCategoryForm") FaqCategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        try {
            faqCategoryService.create(form);
        } catch (FaqValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/edit/{id:\\d+}")
    public String editForm(@PathVariable Long id, Model model) {
        prepareFormModel(model, faqCategoryService.getForm(id), id);
        return FORM_VIEW;
    }

    @PostMapping("/edit/{id:\\d+}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("faqCategoryForm") FaqCategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        try {
            faqCategoryService.update(id, form);
        } catch (FaqValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    // 사용 중인 카테고리는 지우지 않고 목록에 사유를 보여 준다.
    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            faqCategoryService.delete(id);
        } catch (FaqCategoryInUseException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return REDIRECT_LIST;
    }

    private void rejectValidation(BindingResult bindingResult,
                                  FaqValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("faqCategory.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(
                exception.getField(), "faqCategory.invalid", exception.getMessage());
    }

    private void prepareFormModel(Model model, FaqCategoryForm form, Long id) {
        boolean editMode = id != null;
        model.addAttribute("faqCategoryForm", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("faqCategoryId", id);
        model.addAttribute("formAction", editMode
                ? "/admin/faq-categories/edit/" + id
                : "/admin/faq-categories");
        model.addAttribute("pageTitle", editMode ? "FAQ 카테고리 수정" : "FAQ 카테고리 등록");
        model.addAttribute("pageDescription", editMode
                ? "카테고리명과 언어별 번역을 수정합니다."
                : "FAQ를 분류할 새 카테고리를 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "수정 저장" : "등록");
        // 번역 탭 라벨. 다른 관리자 화면과 같은 조각/라벨을 그대로 쓴다.
        model.addAttribute("translationLanguageLabels", AdminTranslationLabels.LANGUAGE_LABELS);
        model.addAttribute("translationTabLabels", AdminTranslationLabels.TAB_LABELS);
    }
}
