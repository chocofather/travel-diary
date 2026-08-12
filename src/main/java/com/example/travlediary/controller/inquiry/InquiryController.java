package com.example.travlediary.controller.inquiry;

import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryForm;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.InquiryType;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.inquiry.InquiryEditConflictException;
import com.example.travlediary.service.inquiry.InquiryService;
import com.example.travlediary.service.inquiry.InquiryValidationException;
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
@RequestMapping("/support/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private static final int PAGE_SIZE = 10;

    private final InquiryService inquiryService;

    @GetMapping
    public String list(@RequestParam(required = false) String page,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model) {
        int requestedPage = parsePage(page);
        Long userId = userDetails.getId();
        long totalCount = inquiryService.countMyInquiries(userId);
        int totalPages = totalPages(totalCount, PAGE_SIZE);
        int currentPage = normalizePage(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<InquiryListItemDto> inquiries = inquiryService.getMyInquiries(
                userId, offset, PAGE_SIZE);

        addPagination(model, currentPage, totalPages, totalCount, PAGE_SIZE);
        model.addAttribute("inquiries", inquiries);
        model.addAttribute("pageTitle", "내 문의내역 | 고객센터");
        return "support/inquiries/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareFormModel(model, new InquiryForm(), null);
        return "support/inquiries/form";
    }

    @PostMapping
    public String create(@ModelAttribute("inquiryForm") InquiryForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, null);
            return "support/inquiries/form";
        }
        try {
            inquiryService.create(form, userDetails.getId());
        } catch (InquiryValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null);
            return "support/inquiries/form";
        }
        return "redirect:/support/inquiries";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        InquiryDetailDto inquiry = inquiryService.getMyInquiry(id, userDetails.getId());
        model.addAttribute("inquiry", inquiry);
        model.addAttribute("pageTitle", inquiry.getSubject() + " | 1:1 문의");
        return "support/inquiries/detail";
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        try {
            InquiryForm form = inquiryService.getEditableMyInquiry(id, userDetails.getId());
            prepareFormModel(model, form, id);
            return "support/inquiries/form";
        } catch (InquiryEditConflictException exception) {
            redirectAttributes.addFlashAttribute("inquiryMessage", exception.getMessage());
            return "redirect:/support/inquiries/" + id;
        }
    }

    @PostMapping("/{id:\\d+}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute("inquiryForm") InquiryForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, id);
            return "support/inquiries/form";
        }
        try {
            inquiryService.updatePendingMyInquiry(id, form, userDetails.getId());
        } catch (InquiryValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id);
            return "support/inquiries/form";
        } catch (InquiryEditConflictException exception) {
            redirectAttributes.addFlashAttribute("inquiryMessage", exception.getMessage());
            return "redirect:/support/inquiries/" + id;
        }
        return "redirect:/support/inquiries/" + id;
    }

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        inquiryService.deletePendingMyInquiry(id, userDetails.getId());
        return "redirect:/support/inquiries";
    }

    private void prepareFormModel(Model model, InquiryForm form, Long inquiryId) {
        boolean editMode = inquiryId != null;
        model.addAttribute("inquiryForm", form);
        model.addAttribute("inquiryTypes", InquiryType.values());
        model.addAttribute("editMode", editMode);
        model.addAttribute("activeInquiryTab", editMode ? "list" : "new");
        model.addAttribute("formTitle", editMode ? "1:1 문의 수정" : "1:1 문의하기");
        model.addAttribute("formDescription", editMode
                ? "답변이 등록되기 전까지 문의 내용을 수정할 수 있습니다."
                : "문의 내용을 자세히 남겨주시면 확인 후 답변해드립니다.");
        model.addAttribute("formAction", editMode
                ? "/support/inquiries/" + inquiryId + "/edit"
                : "/support/inquiries");
        model.addAttribute("cancelUrl", editMode
                ? "/support/inquiries/" + inquiryId
                : "/support/inquiries");
        model.addAttribute("submitLabel", editMode ? "문의 수정" : "문의 등록");
        model.addAttribute("pageTitle", (editMode ? "1:1 문의 수정" : "1:1 문의하기")
                + " | 고객센터");
    }

    private void rejectValidation(BindingResult bindingResult,
                                  InquiryValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("inquiry.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "inquiry.invalid", exception.getMessage());
    }

    private int parsePage(String page) {
        if (page == null || page.isBlank()) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(page.strip()), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private int totalPages(long totalCount, int pageSize) {
        return totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
    }

    private int normalizePage(int requestedPage, int totalPages) {
        return totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
    }

    private void addPagination(Model model, int currentPage, int totalPages,
                               long totalCount, int pageSize) {
        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
    }
}
