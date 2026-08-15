package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.AppealHandleForm;
import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AdminAppealService;
import com.example.travlediary.service.user.AppealValidationException;
import com.example.travlediary.service.user.SanctionValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/appeals")
@RequiredArgsConstructor
public class AdminAppealController {

    private static final int PAGE_SIZE = 20;
    private static final String LIST_VIEW = "admin/appeals/list";
    private static final String DETAIL_VIEW = "admin/appeals/detail";

    private final AdminAppealService adminAppealService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String page,
                       Model model) {
        AppealStatus normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        int requestedPage = parsePage(page);

        long totalCount = adminAppealService.countAppeals(normalizedStatus, normalizedKeyword);
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;

        model.addAttribute("appeals", adminAppealService.getAppeals(
                normalizedStatus, normalizedKeyword, offset, PAGE_SIZE));
        model.addAttribute("currentStatus",
                normalizedStatus == null ? "ALL" : normalizedStatus.name());
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "이의제기 관리");
        return LIST_VIEW;
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        prepareDetailModel(model, id, null);
        return DETAIL_VIEW;
    }

    @PostMapping("/{id:\\d+}/approve")
    public String approve(@PathVariable Long id,
                          @ModelAttribute("handleForm") AppealHandleForm form,
                          @AuthenticationPrincipal CustomUserDetails admin,
                          Model model) {
        try {
            adminAppealService.approve(id, form.getAdminReply(), adminId(admin));
        } catch (AppealValidationException | SanctionValidationException exception) {
            prepareDetailModel(model, id, exception.getMessage());
            return DETAIL_VIEW;
        }
        return "redirect:/admin/appeals/" + id;
    }

    @PostMapping("/{id:\\d+}/reject")
    public String reject(@PathVariable Long id,
                         @ModelAttribute("handleForm") AppealHandleForm form,
                         @AuthenticationPrincipal CustomUserDetails admin,
                         Model model) {
        try {
            adminAppealService.reject(id, form.getAdminReply(), adminId(admin));
        } catch (AppealValidationException exception) {
            prepareDetailModel(model, id, exception.getMessage());
            return DETAIL_VIEW;
        }
        return "redirect:/admin/appeals/" + id;
    }

    private void prepareDetailModel(Model model, Long id, String errorMessage) {
        model.addAttribute("appeal", adminAppealService.getAppeal(id));
        model.addAttribute("handleForm", new AppealHandleForm());
        model.addAttribute("appealError", errorMessage);
        model.addAttribute("pageTitle", "이의제기 상세");
    }

    private Long adminId(CustomUserDetails admin) {
        return admin == null ? null : admin.getId();
    }

    private AppealStatus normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            AppealStatus status = AppealStatus.valueOf(rawStatus.strip().toUpperCase());
            return status == AppealStatus.DRAFT ? null : status;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
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
}
