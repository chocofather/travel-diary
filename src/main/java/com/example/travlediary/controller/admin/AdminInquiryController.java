package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.InquiryAnswerForm;
import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.security.CustomUserDetails;
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

import java.util.List;

@Controller
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private static final int PAGE_SIZE = 20;

    private final InquiryService inquiryService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       Model model) {
        InquiryStatus normalizedStatus = normalizeStatus(status);
        int requestedPage = parsePage(page);
        long totalCount = inquiryService.countAdminInquiries(normalizedStatus);
        int totalPages = totalPages(totalCount);
        int currentPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<InquiryListItemDto> inquiries = inquiryService.getAdminInquiries(
                normalizedStatus, offset, PAGE_SIZE);

        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);
        model.addAttribute("inquiries", inquiries);
        model.addAttribute("currentStatus", normalizedStatus == null ? "ALL" : normalizedStatus.name());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("pageTitle", "1:1 문의 관리");
        return "admin/inquiries/list";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        prepareDetailModel(model, inquiryService.getAdminInquiry(id), null);
        return "admin/inquiries/detail";
    }

    @PostMapping("/{id:\\d+}/answer")
    public String saveAnswer(@PathVariable Long id,
                             @ModelAttribute("answerForm") InquiryAnswerForm form,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        if (bindingResult.hasErrors()) {
            prepareDetailModel(model, inquiryService.getAdminInquiry(id), form);
            return "admin/inquiries/detail";
        }
        try {
            inquiryService.saveAnswer(id, form, userDetails.getId());
        } catch (InquiryValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareDetailModel(model, inquiryService.getAdminInquiry(id), form);
            return "admin/inquiries/detail";
        }
        return "redirect:/admin/inquiries/" + id;
    }

    private void prepareDetailModel(Model model, InquiryDetailDto inquiry,
                                    InquiryAnswerForm submittedForm) {
        InquiryAnswerForm form = submittedForm == null ? new InquiryAnswerForm() : submittedForm;
        if (submittedForm == null && inquiry.hasAnswer()) {
            form.setContent(inquiry.getAnswerContent());
        }
        model.addAttribute("inquiry", inquiry);
        model.addAttribute("answerForm", form);
        model.addAttribute("answerMode", inquiry.hasAnswer() ? "edit" : "create");
        model.addAttribute("pageTitle", "1:1 문의 상세");
    }

    private InquiryStatus normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        String normalized = rawStatus.strip();
        if ("PENDING".equals(normalized)) {
            return InquiryStatus.PENDING;
        }
        if ("ANSWERED".equals(normalized)) {
            return InquiryStatus.ANSWERED;
        }
        return null;
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

    private int totalPages(long totalCount) {
        return totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
    }

    private void rejectValidation(BindingResult bindingResult,
                                  InquiryValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("inquiry.answer.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(),
                "inquiry.answer.invalid", exception.getMessage());
    }
}
