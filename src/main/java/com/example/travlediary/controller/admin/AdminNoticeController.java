package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.notice.NoticeService;
import com.example.travlediary.service.notice.NoticeValidationException;
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
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private static final String LIST_VIEW = "admin/notices/list";
    private static final String FORM_VIEW = "admin/notices/form";
    private static final String REDIRECT_LIST = "redirect:/admin/notices";

    private final NoticeService noticeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("notices", noticeService.getAdminList());
        model.addAttribute("pageTitle", "공지사항 관리");
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareFormModel(model, new NoticeForm(), null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("noticeForm") NoticeForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        try {
            noticeService.create(form, userDetails.getId());
        } catch (NoticeValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        prepareFormModel(model, noticeService.getForm(id), id);
        return FORM_VIEW;
    }

    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("noticeForm") NoticeForm form,
                         BindingResult bindingResult,
                         Model model) {
        try {
            noticeService.update(id, form);
        } catch (NoticeValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id) {
        noticeService.delete(id);
        return REDIRECT_LIST;
    }

    private void rejectValidation(BindingResult bindingResult,
                                  NoticeValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("notice.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "notice.invalid", exception.getMessage());
    }

    private void prepareFormModel(Model model, NoticeForm form, Long id) {
        boolean editMode = id != null;
        model.addAttribute("noticeForm", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("noticeId", id);
        model.addAttribute("formAction", editMode
                ? "/admin/notices/" + id + "/edit"
                : "/admin/notices");
        model.addAttribute("pageTitle", editMode ? "공지사항 수정" : "공지사항 등록");
        model.addAttribute("pageDescription", editMode
                ? "공지사항의 제목, 본문과 상단 고정 여부를 수정합니다."
                : "고객센터에 공개할 새 공지사항을 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "수정 저장" : "등록");
    }
}
