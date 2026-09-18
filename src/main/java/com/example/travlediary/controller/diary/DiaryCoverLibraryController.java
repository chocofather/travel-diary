package com.example.travlediary.controller.diary;

import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDownloadResult;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/diaries/cover-library")
@RequiredArgsConstructor
@Slf4j
public class DiaryCoverLibraryController {

    private final DiaryCoverLibraryQueryService libraryQueryService;
    private final DiaryCoverLibraryDownloadService libraryDownloadService;
    private final DiaryCoverLibraryManagementService libraryManagementService;
    private final DiaryCoverLibraryReportService libraryReportService;
    private static final String FETCH_MODE_HEADER = "Sec-Fetch-Mode";
    private static final String LIBRARY_REDIRECT = "redirect:/diaries/cover-library";

    private final DiaryStickerCatalog diaryStickerCatalog;
    private final DiaryCoverDesignService coverDesignService;
    private final DiaryCoverDesignElementService coverDesignElementService;

    /*
      라이브러리 화면은 나의 여행일기 위 표지 디자인 패널이 불러와 그린다. (fetch)
      주소창·북마크로 직접 들어온 경우(브라우저 페이지 이동)에만 /diaries 로 보내 같은 패널로 연다.
      정렬·쪽 같은 화면 위치와 안내 문구는 그대로 넘긴다.
    */
    @GetMapping
    public String library(@RequestParam(required = false) String sort,
                          @RequestParam(required = false) String page,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          @RequestHeader(name = FETCH_MODE_HEADER, required = false) String fetchMode,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (isPageNavigation(fetchMode)) {
            return DiaryCoverDesignHub.openLibrary("/diaries/cover-library", model,
                    redirectAttributes, "sort", sort, "page", page);
        }
        DiaryCoverLibraryPageDto libraryPage = libraryQueryService.getPublishedPage(
                DiaryCoverLibrarySort.of(sort), pageNumber(page));
        model.addAttribute("libraryPage", libraryPage);
        model.addAttribute("libraryItems", libraryPage.items());
        model.addAttribute("libraryElementsByItem", libraryPage.elementsByItem());
        // 신고는 목록 카드의 ⋯ 메뉴에서 연다. 사진 신고 대상은 함께 공유된 사진만이다.
        model.addAttribute("reportablePhotosByItem",
                reportablePhotosByItem(libraryPage.elementsByItem()));
        model.addAttribute("reportReasons", DiaryCoverLibraryReportReason.values());
        // 카드마다 "내가 공유한 표지인지"를 가리는 기준. (creator_user_id 와 비교한다)
        model.addAttribute("libraryViewerId", userDetails.getId());
        addOwnedDesigns(userDetails.getId(), model);
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "표지 라이브러리");
        return "diary/cover-library/list";
    }

    /**
     * 예전 라이브러리 상세 주소. 상세 화면은 없애고 받기·신고는 목록에서 하므로,
     * 북마크가 깨지지 않도록 라이브러리 목록(나의 여행일기 위 패널)으로 보낸다.
     */
    @GetMapping("/{itemId:\\d+}")
    public String detail(@PathVariable Long itemId,
                         @RequestHeader(name = FETCH_MODE_HEADER, required = false) String fetchMode,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (isPageNavigation(fetchMode)) {
            return DiaryCoverDesignHub.openLibrary("/diaries/cover-library", model, redirectAttributes);
        }
        return LIBRARY_REDIRECT;
    }

    @PostMapping("/{itemId:\\d+}/reports")
    public String report(@PathVariable Long itemId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         DiaryCoverLibraryReportForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        // 신고는 목록에서 하므로 결과(안내 문구)도 목록으로 돌려보낸다. 패널은 그 문구만 읽어 알린다.
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "coverLibraryError", "잘못된 신고 요청입니다.");
            return LIBRARY_REDIRECT;
        }
        try {
            libraryReportService.submitReport(userDetails.getId(), itemId, form);
            redirectAttributes.addFlashAttribute(
                    "coverLibraryMessage", "신고가 접수되었습니다.");
            return LIBRARY_REDIRECT;
        } catch (DiaryCoverLibraryReportException exception) {
            String message = switch (exception.getReason()) {
                case DUPLICATE -> "이미 신고한 항목입니다.";
                case UNAVAILABLE -> "공개 중지되었거나 찾을 수 없는 표지 디자인입니다.";
                case INVALID_PHOTO -> "신고할 공유 사진을 찾을 수 없습니다.";
                case INVALID_REQUEST -> exception.getMessage();
            };
            redirectAttributes.addFlashAttribute("coverLibraryError", message);
            return LIBRARY_REDIRECT;
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 신고 접수에 실패했습니다. itemId={}", itemId, exception);
            redirectAttributes.addFlashAttribute(
                    "coverLibraryError", "신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return LIBRARY_REDIRECT;
        }
    }

    @GetMapping("/mine")
    public String mine(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @RequestHeader(name = FETCH_MODE_HEADER, required = false) String fetchMode,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (isPageNavigation(fetchMode)) {
            return DiaryCoverDesignHub.openLibrary(
                    "/diaries/cover-library/mine", model, redirectAttributes);
        }
        DiaryCoverLibraryMineDto mine = libraryQueryService.getMine(userDetails.getId());
        model.addAttribute("libraryItems", mine.items());
        model.addAttribute("libraryElementsByItem", mine.elementsByItem());
        addOwnedDesigns(userDetails.getId(), model);
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "내 공유 디자인");
        return "diary/cover-library/mine";
    }

    @PostMapping("/{itemId:\\d+}/download")
    public String download(@PathVariable Long itemId,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        try {
            DiaryCoverLibraryDownloadResult result =
                    libraryDownloadService.download(userDetails.getId(), itemId);
            DiaryCoverDesign created = result.design();
            // 받은 디자인은 라이브러리 목록 아래 "내 보유 디자인"에 놓인 모습으로 보여 준다.
            redirectAttributes.addFlashAttribute(
                    "coverDesignMessage", "내 디자인에 추가했습니다. 표지를 눌러 바로 편집할 수 있어요.");
            redirectAttributes.addFlashAttribute("coverDesignAddedId", created.getId());
            // 받은 표지와 서버가 반영한 다운로드 수. 패널이 그 카드만 바로 고쳐 그린다.
            redirectAttributes.addFlashAttribute("coverLibraryDownloadedItemId", itemId);
            redirectAttributes.addFlashAttribute("coverLibraryDownloadCount", result.downloadCount());
            return "redirect:/diaries/cover-library#cover-library-owned-" + created.getId();
        } catch (ResponseStatusException exception) {
            String message;
            if (HttpStatus.FORBIDDEN.equals(exception.getStatusCode())) {
                message = "표지 디자인을 받을 권한이 없습니다.";
            } else if (HttpStatus.UNPROCESSABLE_ENTITY.equals(exception.getStatusCode())) {
                message = "직접 공유한 디자인은 다시 받을 수 없습니다.";
            } else if (HttpStatus.CONFLICT.equals(exception.getStatusCode())) {
                message = "이미 내 보유 디자인에 있는 표지입니다.";
            } else {
                message = "공개 중지되었거나 찾을 수 없는 표지 디자인입니다.";
            }
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

    /**
     * 라이브러리 화면 아래 "내 보유 디자인". 내 표지 디자인 페이지와 같은 조회를 그대로 쓴다.
     * (공유 스냅샷이 아니라 로그인 회원이 편집/사용할 수 있는 diary_cover_designs 다)
     */
    private void addOwnedDesigns(Long userId, Model model) {
        List<DiaryCoverDesign> designs = coverDesignService.getMyDesigns(userId);
        List<Long> designIds = designs.stream().map(DiaryCoverDesign::getId).toList();
        model.addAttribute("coverDesigns", designs);
        model.addAttribute("coverElementsByDesign",
                coverDesignElementService.getElementsByDesign(designIds, userId));
        // 지금 보유 중인 라이브러리 표지. 같은 목록에서 출처만 모으므로 따로 묻지 않는다.
        model.addAttribute("ownedLibraryItemIds", ownedLibraryItemIds(designs));
    }

    /**
     * 회원이 지금 가지고 있는 디자인 중 라이브러리에서 받은 것들의 원본 표지 번호.
     * (다운로드 이력이 아니라 현재 보유 기준이다. 받은 디자인을 지웠다면 들어가지 않는다)
     */
    static Set<Long> ownedLibraryItemIds(List<DiaryCoverDesign> designs) {
        return designs.stream()
                .map(DiaryCoverDesign::getSourceLibraryItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 표지마다 신고 대상으로 고를 수 있는 사진. 라이브러리에 함께 공유한(INCLUDED) 사진만 고른다.
     * (예전 상세 화면과 같은 기준이다. 실제 검증은 신고 서비스가 다시 한다)
     */
    private Map<Long, List<DiaryCoverLibraryElement>> reportablePhotosByItem(
            Map<Long, List<DiaryCoverLibraryElement>> elementsByItem) {
        Map<Long, List<DiaryCoverLibraryElement>> photos = new LinkedHashMap<>();
        elementsByItem.forEach((itemId, elements) -> photos.put(itemId, elements.stream()
                .filter(element -> "PHOTO".equals(element.getElementType()))
                .filter(element -> element.getPhotoShareMode()
                        == DiaryCoverLibraryPhotoShareMode.INCLUDED)
                .filter(element -> element.getPhotoAssetId() != null)
                .toList()));
        return photos;
    }

    /**
     * 브라우저가 페이지 자체를 여는 요청인지. (Sec-Fetch-Mode: navigate)
     * 패널의 fetch 요청과, 이 헤더를 보내지 않는 오래된 브라우저는 지금처럼 화면을 그대로 돌려준다.
     */
    private boolean isPageNavigation(String fetchMode) {
        return "navigate".equalsIgnoreCase(fetchMode);
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
