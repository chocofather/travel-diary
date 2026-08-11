package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.faq.FaqService;
import com.example.travlediary.service.faq.FaqValidationException;
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

@Controller
@RequestMapping("/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private static final String LIST_VIEW = "admin/faqs/list";
    private static final String FORM_VIEW = "admin/faqs/form";
    private static final String REDIRECT_LIST = "redirect:/admin/faqs";

    private final FaqService faqService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("faqs", faqService.getAdminList());
        model.addAttribute("pageTitle", "자주 묻는 질문 관리");
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareFormModel(model, new FaqForm(), null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("faqForm") FaqForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        try {
            faqService.create(form, userDetails.getId());
        } catch (FaqValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        prepareFormModel(model, faqService.getForm(id), id);
        return FORM_VIEW;
    }

    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("faqForm") FaqForm form,
                         BindingResult bindingResult,
                         Model model) {
        try {
            faqService.update(id, form);
        } catch (FaqValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id) {
        faqService.delete(id);
        return REDIRECT_LIST;
    }

    private void rejectValidation(BindingResult bindingResult,
                                  FaqValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("faq.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "faq.invalid", exception.getMessage());
    }

    private void prepareFormModel(Model model, FaqForm form, Long id) {
        boolean editMode = id != null;
        model.addAttribute("faqForm", form);
        model.addAttribute("categories", faqService.getCategories());
        model.addAttribute("editMode", editMode);
        model.addAttribute("faqId", id);
        model.addAttribute("formAction", editMode
                ? "/admin/faqs/" + id + "/edit"
                : "/admin/faqs");
        model.addAttribute("pageTitle", editMode ? "자주 묻는 질문 수정" : "자주 묻는 질문 등록");
        model.addAttribute("pageDescription", editMode
                ? "질문, 답변, 노출 순서와 공개 상태를 수정합니다."
                : "고객센터에 공개할 자주 묻는 질문을 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "수정 저장" : "등록");
    }
}
