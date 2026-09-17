package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DiaryCoverLibraryModerationForm;
import com.example.travlediary.dto.DiaryCoverLibraryReportDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportStatusFilter;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverLibraryModerationException;
import com.example.travlediary.service.diary.DiaryCoverLibraryModerationService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/cover-library/reports")
@RequiredArgsConstructor
@Slf4j
public class AdminDiaryCoverLibraryReportController {

    private final DiaryCoverLibraryModerationService moderationService;
    private final DiaryStickerCatalog diaryStickerCatalog;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       @AuthenticationPrincipal CustomUserDetails admin,
                       Model model) {
        DiaryCoverLibraryReportPageDto reportPage = moderationService.getReports(
                admin.getId(), DiaryCoverLibraryReportStatusFilter.of(status),
                pageNumber(page));
        model.addAttribute("reportPage", reportPage);
        model.addAttribute("reports", reportPage.items());
        model.addAttribute("pageTitle", "표지 라이브러리 신고 관리");
        return "admin/cover-library-reports/list";
    }

    @GetMapping("/{reportId:\\d+}")
    public String detail(@PathVariable Long reportId,
                         @AuthenticationPrincipal CustomUserDetails admin,
                         Model model) {
        populateDetail(admin.getId(), reportId, model);
        return "admin/cover-library-reports/detail";
    }

    @PostMapping("/{reportId:\\d+}/process")
    public String process(@PathVariable Long reportId,
                          @AuthenticationPrincipal CustomUserDetails admin,
                          DiaryCoverLibraryModerationForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "coverReportError", "잘못된 처리 요청입니다.");
            return "redirect:/admin/cover-library/reports/" + reportId;
        }
        try {
            moderationService.process(admin.getId(), reportId, form);
            redirectAttributes.addFlashAttribute(
                    "coverReportMessage", "신고 처리를 완료했습니다.");
        } catch (DiaryCoverLibraryModerationException exception) {
            redirectAttributes.addFlashAttribute("coverReportError", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 신고 처리에 실패했습니다. reportId={}",
                    reportId, exception);
            redirectAttributes.addFlashAttribute(
                    "coverReportError", "신고를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/admin/cover-library/reports/" + reportId;
    }

    private void populateDetail(Long adminId, Long reportId, Model model) {
        DiaryCoverLibraryReportDetailDto detail =
                moderationService.getReportDetail(adminId, reportId);
        model.addAttribute("reportDetail", detail);
        model.addAttribute("report", detail.report());
        model.addAttribute("libraryItem", detail.item());
        model.addAttribute("libraryElements", detail.elements());
        model.addAttribute("targetPhotoAsset", detail.targetPhotoAsset());
        model.addAttribute("reporterDisplayName", detail.reporterDisplayName());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "표지 라이브러리 신고 #" + reportId);
    }

    private int pageNumber(String page) {
        if (page == null || page.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(page.strip()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
