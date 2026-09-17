package com.example.travlediary.controller.diary;

import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverLibraryDownloadService;
import com.example.travlediary.service.diary.DiaryCoverLibraryManagementService;
import com.example.travlediary.service.diary.DiaryCoverLibraryQueryService;
import com.example.travlediary.service.diary.DiaryCoverLibraryReportException;
import com.example.travlediary.service.diary.DiaryCoverLibraryReportService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

@Controller
@RequestMapping("/diaries/cover-library")
@RequiredArgsConstructor
@Slf4j
public class DiaryCoverLibraryController {

    private final DiaryCoverLibraryQueryService libraryQueryService;
    private final DiaryCoverLibraryDownloadService libraryDownloadService;
    private final DiaryCoverLibraryManagementService libraryManagementService;
    private final DiaryCoverLibraryReportService libraryReportService;
    private final DiaryStickerCatalog diaryStickerCatalog;

    @GetMapping
    public String library(@RequestParam(required = false) String sort,
                          @RequestParam(required = false) String page,
                          Model model) {
        DiaryCoverLibraryPageDto libraryPage = libraryQueryService.getPublishedPage(
                DiaryCoverLibrarySort.of(sort), pageNumber(page));
        model.addAttribute("libraryPage", libraryPage);
        model.addAttribute("libraryItems", libraryPage.items());
        model.addAttribute("libraryElementsByItem", libraryPage.elementsByItem());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "표지 라이브러리");
        return "diary/cover-library/list";
    }

    @GetMapping("/{itemId:\\d+}")
    public String detail(@PathVariable Long itemId, Model model) {
        DiaryCoverLibraryDetailDto detail =
                libraryQueryService.getPublishedDetail(itemId);
        model.addAttribute("libraryItem", detail.item());
        model.addAttribute("libraryElements", detail.elements());
        model.addAttribute("reportablePhotoElements", detail.elements().stream()
                .filter(element -> "PHOTO".equals(element.getElementType()))
                .filter(element -> element.getPhotoShareMode()
                        == DiaryCoverLibraryPhotoShareMode.INCLUDED)
                .filter(element -> element.getPhotoAssetId() != null)
                .toList());
        model.addAttribute("reportReasons", DiaryCoverLibraryReportReason.values());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", detail.item().getTitle());
        return "diary/cover-library/detail";
    }

    @PostMapping("/{itemId:\\d+}/reports")
    public String report(@PathVariable Long itemId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         DiaryCoverLibraryReportForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "coverLibraryError", "잘못된 신고 요청입니다.");
            return "redirect:/diaries/cover-library/" + itemId;
        }
        try {
            libraryReportService.submitReport(userDetails.getId(), itemId, form);
            redirectAttributes.addFlashAttribute(
                    "coverLibraryMessage", "신고가 접수되었습니다.");
            return "redirect:/diaries/cover-library/" + itemId;
        } catch (DiaryCoverLibraryReportException exception) {
            String message = switch (exception.getReason()) {
                case DUPLICATE -> "이미 신고한 항목입니다.";
                case UNAVAILABLE -> "공개 중지되었거나 찾을 수 없는 표지 디자인입니다.";
                case INVALID_PHOTO -> "신고할 공유 사진을 찾을 수 없습니다.";
                case INVALID_REQUEST -> exception.getMessage();
            };
            redirectAttributes.addFlashAttribute("coverLibraryError", message);
            return exception.getReason() == DiaryCoverLibraryReportException.Reason.UNAVAILABLE
                    ? "redirect:/diaries/cover-library"
                    : "redirect:/diaries/cover-library/" + itemId;
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 신고 접수에 실패했습니다. itemId={}", itemId, exception);
            redirectAttributes.addFlashAttribute(
                    "coverLibraryError", "신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/diaries/cover-library/" + itemId;
        }
    }

    @GetMapping("/mine")
    public String mine(@AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model) {
        DiaryCoverLibraryMineDto mine = libraryQueryService.getMine(userDetails.getId());
        model.addAttribute("libraryItems", mine.items());
        model.addAttribute("libraryElementsByItem", mine.elementsByItem());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "내 공유 디자인");
        return "diary/cover-library/mine";
    }

    @PostMapping("/{itemId:\\d+}/download")
    public String download(@PathVariable Long itemId,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        try {
            DiaryCoverDesign created =
                    libraryDownloadService.download(userDetails.getId(), itemId);
            redirectAttributes.addFlashAttribute(
                    "coverDesignMessage", "내 디자인에 추가했습니다.");
            return "redirect:/diaries/cover-designs/" + created.getId() + "/edit";
        } catch (ResponseStatusException exception) {
            String message = HttpStatus.FORBIDDEN.equals(exception.getStatusCode())
                    ? "표지 디자인을 받을 권한이 없습니다."
                    : "공개 중지되었거나 찾을 수 없는 표지 디자인입니다.";
            redirectAttributes.addFlashAttribute("coverLibraryError", message);
            return "redirect:/diaries/cover-library";
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 다운로드에 실패했습니다. itemId={}", itemId, exception);
            redirectAttributes.addFlashAttribute("coverLibraryError",
                    "표지 디자인을 내 디자인에 추가하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/diaries/cover-library";
        }
    }

    @PostMapping("/{itemId:\\d+}/withdraw")
    public String withdraw(@PathVariable Long itemId,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        return changeStatus(itemId, userDetails, redirectAttributes,
                () -> libraryManagementService.withdraw(userDetails.getId(), itemId),
                "라이브러리 공개를 중지했습니다.");
    }

    @PostMapping("/{itemId:\\d+}/republish")
    public String republish(@PathVariable Long itemId,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        return changeStatus(itemId, userDetails, redirectAttributes,
                () -> libraryManagementService.republish(userDetails.getId(), itemId),
                "라이브러리에 다시 공개했습니다.");
    }

    @PostMapping("/{itemId:\\d+}/delete")
    public String delete(@PathVariable Long itemId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        return changeStatus(itemId, userDetails, redirectAttributes,
                () -> libraryManagementService.delete(userDetails.getId(), itemId),
                "공유 디자인을 삭제했습니다.");
    }

    @GetMapping("/assets/{assetId:\\d+}")
    @ResponseBody
    public ResponseEntity<Resource> asset(@PathVariable Long assetId) {
        DiaryCoverLibraryAssetFile file = libraryQueryService.getActiveAsset(assetId);
        Resource resource = new FileSystemResource(file.path());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60))
                        .mustRevalidate().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
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

    private String changeStatus(Long itemId,
                                CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes,
                                Runnable change,
                                String successMessage) {
        try {
            change.run();
            redirectAttributes.addFlashAttribute("coverLibraryMessage", successMessage);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("coverLibraryError",
                    "관리할 공유 디자인을 찾을 수 없거나 상태를 변경할 수 없습니다.");
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 상태 변경에 실패했습니다. itemId={}, userId={}",
                    itemId, userDetails.getId(), exception);
            redirectAttributes.addFlashAttribute("coverLibraryError",
                    "공유 디자인 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/diaries/cover-library/mine";
    }
}
