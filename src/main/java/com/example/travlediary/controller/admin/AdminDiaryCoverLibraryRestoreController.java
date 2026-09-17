package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DiaryCoverLibraryRestoreForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverLibraryModerationException;
import com.example.travlediary.service.diary.DiaryCoverLibraryModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/cover-library")
@RequiredArgsConstructor
@Slf4j
public class AdminDiaryCoverLibraryRestoreController {

    private static final String REPORTS_PATH = "/admin/cover-library/reports";

    private final DiaryCoverLibraryModerationService moderationService;

    @PostMapping("/items/{itemId:\\d+}/restore")
    public String restoreItem(@PathVariable Long itemId,
                              @AuthenticationPrincipal CustomUserDetails admin,
                              DiaryCoverLibraryRestoreForm form,
                              RedirectAttributes redirectAttributes) {
        try {
            Long reportId = moderationService.restoreItem(admin.getId(), itemId, form);
            redirectAttributes.addFlashAttribute(
                    "coverReportMessage", "표지 차단을 해제했습니다.");
            return redirectToReport(reportId);
        } catch (DiaryCoverLibraryModerationException exception) {
            redirectAttributes.addFlashAttribute("coverReportError", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 item 복구에 실패했습니다. itemId={}",
                    itemId, exception);
            redirectAttributes.addFlashAttribute(
                    "coverReportError", "표지 차단을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:" + REPORTS_PATH;
    }

    @PostMapping("/photo-assets/{assetId:\\d+}/restore")
    public String restorePhoto(@PathVariable Long assetId,
                               @AuthenticationPrincipal CustomUserDetails admin,
                               DiaryCoverLibraryRestoreForm form,
                               RedirectAttributes redirectAttributes) {
        try {
            Long reportId = moderationService.restorePhoto(admin.getId(), assetId, form);
            redirectAttributes.addFlashAttribute(
                    "coverReportMessage", "사진 차단을 해제했습니다.");
            return redirectToReport(reportId);
        } catch (DiaryCoverLibraryModerationException exception) {
            redirectAttributes.addFlashAttribute("coverReportError", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 PHOTO 복구에 실패했습니다. assetId={}",
                    assetId, exception);
            redirectAttributes.addFlashAttribute(
                    "coverReportError", "사진 차단을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:" + REPORTS_PATH;
    }

    private String redirectToReport(Long reportId) {
        if (reportId == null) {
            return "redirect:" + REPORTS_PATH;
        }
        return "redirect:" + REPORTS_PATH + "/" + reportId;
    }
}
