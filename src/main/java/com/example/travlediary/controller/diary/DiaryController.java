package com.example.travlediary.controller.diary;

import com.example.travlediary.dto.DiaryListPageDto;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryElementService;
import com.example.travlediary.service.diary.DiaryPageService;
import com.example.travlediary.service.diary.DiaryService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/diaries")
public class DiaryController {

    /** 대표 이미지 저장 위치. 다른 업로드와 섞지 않는다. */
    private static final String COVER_IMAGE_DIRECTORY = "diary-covers";
    /** 페이지에 붙이는 사진 저장 위치 */
    private static final String PAGE_IMAGE_DIRECTORY = "diary-pages";
    /** 한 번에 펼쳐 보여주는 페이지 수 (좌/우 두 장) */
    private static final int SPREAD_SIZE = 2;
    private static final String PHOTO_ELEMENT_TYPE = "PHOTO";
    private static final String STICKER_ELEMENT_TYPE = "STICKER";
    /** 스티커를 처음 붙이는 자리/크기. 종이 가운데 부근에서 조금씩 어긋나게 놓는다. */
    private static final BigDecimal STICKER_SIZE = new BigDecimal("0.18000");
    private static final BigDecimal STICKER_CENTER = new BigDecimal("0.41000");
    private static final BigDecimal STICKER_OFFSET_STEP = new BigDecimal("0.04000");
    private static final int STICKER_OFFSET_CYCLE = 5;

    private final DiaryService diaryService;
    private final DiaryPageService diaryPageService;
    private final DiaryElementService diaryElementService;
    private final FileUploadService fileUploadService;
    private final DiaryStickerCatalog diaryStickerCatalog;

    @Value("${custom.upload-path}")
    private String uploadPath;

    /**
     * 내 여행일기 목록 (본인 다이어리만).
     * q 로 제목/한 줄 메모/본문을 함께 찾고, 12권씩 나눠 보여준다.
     */
    @GetMapping
    public String diaryList(@RequestParam(name = "q", required = false) String keyword,
                            @RequestParam(name = "page", required = false) String page,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        DiaryListPageDto diaryPage =
                diaryService.getMyDiaryPage(userDetails.getId(), keyword, pageNumber(page));

        model.addAttribute("diaryList", diaryPage.items());
        model.addAttribute("diaryPage", diaryPage);
        model.addAttribute("pageTitle", "나의 여행일기");
        return "diary/list";
    }

    /** 쪽 번호는 1부터. 비어 있거나 숫자가 아니거나 1보다 작으면 첫 쪽으로 본다. */
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

    /** 다이어리 한 권 펼쳐보기. 소유권은 서비스에서 확인한다. */
    @GetMapping("/{diaryId:\\d+}")
    public String diaryDetail(@PathVariable Long diaryId,
                              @RequestParam(defaultValue = "0") int spread,
                              @RequestParam(defaultValue = "false") boolean edit,
                              @RequestParam(required = false) Integer page,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        Long userId = userDetails.getId();
        Diary diary = diaryService.getMyDiary(diaryId, userId);
        List<DiaryPage> pages = diaryPageService.getPages(diaryId, userId);

        // 한 펼침 = 두 장. 잘못된 spread 값은 오류 대신 유효 범위로 맞춘다.
        int totalSpreads = Math.max(1, (pages.size() + SPREAD_SIZE - 1) / SPREAD_SIZE);
        int currentSpread = Math.min(Math.max(spread, 0), totalSpreads - 1);
        int leftIndex = currentSpread * SPREAD_SIZE;

        DiaryPage leftPage = leftIndex < pages.size() ? pages.get(leftIndex) : null;
        DiaryPage rightPage = leftIndex + 1 < pages.size() ? pages.get(leftIndex + 1) : null;

        model.addAttribute("diary", diary);
        model.addAttribute("diaryPages", pages);
        model.addAttribute("leftPage", leftPage);
        model.addAttribute("rightPage", rightPage);
        // 펼쳐 놓은 두 장만 요소를 읽는다. 순서(z_index, id)는 조회 결과를 그대로 쓴다.
        // 편집 모드는 한 장만 그리므로 아래에서 그 장만 따로 읽는다.
        model.addAttribute("leftElements", edit ? List.of() : pageElements(diaryId, leftPage, userId));
        model.addAttribute("rightElements", edit ? List.of() : pageElements(diaryId, rightPage, userId));
        model.addAttribute("currentSpread", currentSpread);
        model.addAttribute("totalSpreads", totalSpreads);
        model.addAttribute("hasPreviousSpread", currentSpread > 0);
        model.addAttribute("hasNextSpread", currentSpread < totalSpreads - 1);
        // 편집 여부는 화면 상태일 뿐이다. 실제 수정 권한은 각 저장 경로에서 다시 확인한다.
        model.addAttribute("editMode", edit);
        if (edit) {
            addEditPageAttributes(diaryId, userId, pages, leftPage, rightPage, page, model);
            // 스티커 picker 목록(분류별). 저장 가능한 스티커는 이 목록이 그대로 허용 목록이다.
            model.addAttribute("diaryStickerCategories", diaryStickerCatalog.getCategories());
        }
        model.addAttribute("pageTitle", diary.getTitle() + " | 나의 여행일기");
        return "diary/detail";
    }

    /**
     * 편집 모드는 실제 페이지 한 장만 크게 보여준다.
     * 편집 대상은 요청 값(pageOrder)을 그대로 믿지 않고, 본인 다이어리에서 읽어 온 목록 안에서만 고른다.
     */
    private void addEditPageAttributes(Long diaryId,
                                       Long userId,
                                       List<DiaryPage> pages,
                                       DiaryPage leftPage,
                                       DiaryPage rightPage,
                                       Integer requestedPageOrder,
                                       Model model) {
        int editIndex = -1;
        if (requestedPageOrder != null) {
            for (int i = 0; i < pages.size(); i++) {
                if (requestedPageOrder.equals(pages.get(i).getPageOrder())) {
                    editIndex = i;
                    break;
                }
            }
        }
        if (editIndex < 0) {
            // 요청한 순서가 목록에 없으면 지금 펼친 장(왼쪽 → 오른쪽)으로 되돌린다.
            DiaryPage fallback = leftPage != null ? leftPage : rightPage;
            editIndex = fallback == null ? -1 : pages.indexOf(fallback);
        }

        DiaryPage editPage = editIndex >= 0 ? pages.get(editIndex) : null;
        model.addAttribute("editPage", editPage);
        model.addAttribute("editElements", pageElements(diaryId, editPage, userId));
        model.addAttribute("editPageNumber", editIndex + 1);
        model.addAttribute("hasPreviousPage", editIndex > 0);
        model.addAttribute("hasNextPage", editIndex >= 0 && editIndex < pages.size() - 1);
        model.addAttribute("previousPageOrder",
                editIndex > 0 ? pages.get(editIndex - 1).getPageOrder() : null);
        model.addAttribute("nextPageOrder",
                editIndex >= 0 && editIndex < pages.size() - 1
                        ? pages.get(editIndex + 1).getPageOrder() : null);
        // 편집을 마치면 이 장이 들어 있는 펼침으로 돌아간다. (읽기 모드의 spread 계산과 같은 기준)
        model.addAttribute("editSpread", Math.max(editIndex, 0) / SPREAD_SIZE);
    }

    /** 다이어리 기본정보 수정 화면 */
    @GetMapping("/{diaryId:\\d+}/edit")
    public String editDiaryForm(@PathVariable Long diaryId,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {
        Diary diary = diaryService.getMyDiary(diaryId, userDetails.getId());
        model.addAttribute("diaryForm", diary);
        model.addAttribute("diaryId", diaryId);
        model.addAttribute("currentCoverImageUrl", diary.getCoverImageUrl());
        model.addAttribute("coverStyles", DiaryCoverStyle.values());
        model.addAttribute("pageTitle", "여행일기 수정");
        return "diary/edit";
    }

    /** 다이어리 기본정보 수정. 소유자와 표지는 요청 값을 그대로 믿지 않는다. */
    @PostMapping("/{diaryId:\\d+}/update")
    public String updateDiary(@PathVariable Long diaryId,
                              @ModelAttribute("diaryForm") Diary diaryForm,
                              BindingResult bindingResult,
                              @RequestParam(value = "coverImage", required = false)
                              MultipartFile coverImage,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        Long userId = userDetails.getId();
        Diary existing = diaryService.getMyDiary(diaryId, userId);
        if (bindingResult.hasErrors()) {
            return renderEditForm(model, diaryId, existing, "여행 기간을 올바르게 입력해 주세요.");
        }

        // 이번 요청에서 새로 저장한 표지만 추적한다.
        String savedCoverImageUrl = null;
        try {
            requirePagesInsidePeriod(diaryId, userId, diaryForm.getStartDate(), diaryForm.getEndDate());
            if (coverImage != null && !coverImage.isEmpty()) {
                savedCoverImageUrl = fileUploadService.saveFile(coverImage, COVER_IMAGE_DIRECTORY);
            }

            Diary diary = new Diary();
            diary.setTitle(diaryForm.getTitle());
            diary.setStartDate(diaryForm.getStartDate());
            diary.setEndDate(diaryForm.getEndDate());
            // 새 표지를 고르지 않았으면 기존 표지를 그대로 둔다.
            diary.setCoverImageUrl(savedCoverImageUrl != null
                    ? savedCoverImageUrl : existing.getCoverImageUrl());
            // 표지 스타일은 고른 값을 쓰고, 값이 없으면 지금 쓰던 스타일을 유지한다.
            diary.setCoverStyle(diaryForm.getCoverStyle() != null && !diaryForm.getCoverStyle().isBlank()
                    ? diaryForm.getCoverStyle() : existing.getCoverStyle());
            diaryService.update(diaryId, userId, diary);
        } catch (ResponseStatusException exception) {
            deleteStoredFile(savedCoverImageUrl);
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                return renderEditForm(model, diaryId, diaryForm, exception.getReason());
            }
            throw exception;
        } catch (RuntimeException exception) {
            deleteStoredFile(savedCoverImageUrl);
            throw exception;
        }

        // DB 수정이 끝난 뒤에 예전 표지 파일을 정리한다.
        if (savedCoverImageUrl != null) {
            deleteStoredFile(existing.getCoverImageUrl());
        }
        // 다이어리 설정은 목록의 ⋯ 메뉴에서 들어오므로 저장 뒤에도 목록으로 돌아간다.
        return "redirect:/diaries";
    }

    /** 다이어리 삭제. 페이지/요소 행은 FK CASCADE 로 지워지므로 실제 파일만 따로 정리한다. */
    @PostMapping("/{diaryId:\\d+}/delete")
    public String deleteDiary(@PathVariable Long diaryId,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        Long userId = userDetails.getId();
        Diary diary = diaryService.getMyDiary(diaryId, userId);

        // 삭제 전에 정리할 파일 경로를 모두 확보한다.
        List<String> imageUrls = new ArrayList<>();
        if (diary.getCoverImageUrl() != null) {
            imageUrls.add(diary.getCoverImageUrl());
        }
        for (DiaryPage page : diaryPageService.getPages(diaryId, userId)) {
            diaryElementService.getElements(diaryId, page.getId(), userId).stream()
                    .filter(element -> PHOTO_ELEMENT_TYPE.equals(element.getElementType()))
                    .map(DiaryElement::getImageUrl)
                    .forEach(imageUrls::add);
        }

        diaryService.delete(diaryId, userId);

        // DB 삭제가 끝난 뒤 파일을 정리한다. (정리 실패는 요청을 깨뜨리지 않는다)
        imageUrls.forEach(this::deleteStoredFile);
        redirectAttributes.addFlashAttribute("diaryMessage", "여행일기가 삭제되었습니다.");
        return "redirect:/diaries";
    }

    /** 여행 기간을 줄일 때 기존 페이지가 밖으로 밀려나지 않는지 확인한다. */
    private void requirePagesInsidePeriod(Long diaryId, Long userId,
                                          LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return; // 필수값 검증은 서비스가 담당한다.
        }
        boolean hasOutsidePage = diaryPageService.getPages(diaryId, userId).stream()
                .anyMatch(page -> page.getPageDate().isBefore(startDate)
                        || page.getPageDate().isAfter(endDate));
        if (hasOutsidePage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "여행 기간 밖으로 밀려나는 페이지가 있어 기간을 바꿀 수 없습니다.");
        }
    }

    /** 입력값을 그대로 둔 채 오류 메시지와 함께 수정 화면을 다시 보여준다. */
    private String renderEditForm(Model model, Long diaryId, Diary diaryForm, String errorMessage) {
        model.addAttribute("diaryForm", diaryForm);
        model.addAttribute("diaryId", diaryId);
        model.addAttribute("diaryError", errorMessage);
        model.addAttribute("coverStyles", DiaryCoverStyle.values());
        model.addAttribute("pageTitle", "여행일기 수정");
        return "diary/edit";
    }

    /** 페이지 날짜/배경 수정. 순서(pageOrder)는 기존 값을 그대로 유지한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/update")
    public String updatePage(@PathVariable Long diaryId,
                             @PathVariable Long pageId,
                             @ModelAttribute DiaryPage pageForm,
                             BindingResult bindingResult,
                             @RequestParam(defaultValue = "0") int spread,
                             @RequestParam(required = false) Integer page,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "페이지 날짜를 올바르게 선택해 주세요.");
            return redirectToEditPage(diaryId, spread, page);
        }

        Long userId = userDetails.getId();
        try {
            DiaryPage existing = diaryPageService.getPage(diaryId, pageId, userId);

            DiaryPage changed = new DiaryPage();
            changed.setPageDate(pageForm.getPageDate());
            changed.setBackgroundType(pageForm.getBackgroundType());
            // 종이색은 비어 있으면 기본 종이색(NULL)이 된다. 형식 확인은 서비스가 한다.
            changed.setPaperColor(pageForm.getPaperColor());
            // 순서는 요청 값을 쓰지 않고 기존 값을 유지한다.
            changed.setPageOrder(existing.getPageOrder());
            diaryPageService.update(diaryId, pageId, userId, changed);
        } catch (ResponseStatusException exception) {
            return redirectWithElementError(exception, diaryId, spread, page, redirectAttributes);
        }
        return redirectToEditPage(diaryId, spread, page);
    }

    /** 페이지 삭제. 요소 행은 FK CASCADE 로 지워지므로 사진 파일만 따로 정리한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/delete")
    public String deletePage(@PathVariable Long diaryId,
                             @PathVariable Long pageId,
                             @RequestParam(defaultValue = "0") int spread,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        Long userId = userDetails.getId();
        List<String> photoImageUrls;
        try {
            // 삭제 전에 정리할 사진 경로를 먼저 확보한다. (소유권·소속은 서비스가 확인)
            photoImageUrls = diaryElementService.getElements(diaryId, pageId, userId).stream()
                    .filter(element -> PHOTO_ELEMENT_TYPE.equals(element.getElementType()))
                    .map(DiaryElement::getImageUrl)
                    .toList();
            diaryPageService.delete(diaryId, pageId, userId);
        } catch (ResponseStatusException exception) {
            return redirectWithElementError(exception, diaryId, spread, redirectAttributes);
        }

        // DB 삭제가 끝난 뒤 파일을 정리한다. (정리 실패는 요청을 깨뜨리지 않는다)
        photoImageUrls.forEach(this::deleteStoredFile);
        return redirectToSpread(diaryId, spread);
    }

    /**
     * 본문 자동저장. 종이에 쓴 글은 요소가 아니라 diary_pages.content 에 저장한다.
     * 날짜/배경을 바꾸는 페이지 수정과 섞이지 않도록 경로를 따로 둔다.
     */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/content")
    @ResponseBody
    public ResponseEntity<?> savePageContent(@PathVariable Long diaryId,
                                             @PathVariable Long pageId,
                                             @RequestParam(required = false) String content,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            // 소유권·페이지 소속 확인과 정리(sanitize)는 서비스가 담당한다.
            diaryPageService.updateContent(diaryId, pageId, userDetails.getId(), content);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "본문을 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.noContent().build();
    }

    /** 날짜 옆 한 줄 메모 자동저장. 본문 저장과 같은 방식으로 값만 받는다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/header")
    @ResponseBody
    public ResponseEntity<?> savePageHeader(@PathVariable Long diaryId,
                                            @PathVariable Long pageId,
                                            @RequestParam(required = false) String pageHeader,
                                            @RequestParam(required = false) String pageHeaderFont,
                                            @RequestParam(defaultValue = "false") boolean pageHeaderBold,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            // 소유권·페이지 소속 확인과 길이/글꼴 검증은 서비스가 담당한다.
            diaryPageService.updatePageHeader(diaryId, pageId, userDetails.getId(),
                    pageHeader, pageHeaderFont, pageHeaderBold);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "한 줄 메모를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.noContent().build();
    }

    /** 페이지에 사진(PHOTO)을 한 장 추가한다. 사진 한 장이 요소 한 행이다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/photo")
    public String createPhotoElement(@PathVariable Long diaryId,
                                     @PathVariable Long pageId,
                                     @RequestParam(value = "image", required = false)
                                     MultipartFile image,
                                     @RequestParam(defaultValue = "0") int spread,
                                     @RequestParam(required = false) Integer page,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        if (image == null || image.isEmpty()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "사진을 선택해 주세요.");
            return redirectToEditPage(diaryId, spread, page);
        }

        // 이번 요청에서 저장한 파일만 추적해 실패 시 정리한다.
        String savedImageUrl = null;
        try {
            savedImageUrl = fileUploadService.saveFile(image, PAGE_IMAGE_DIRECTORY);

            DiaryElement element = new DiaryElement();
            element.setElementType(PHOTO_ELEMENT_TYPE);
            element.setImageUrl(savedImageUrl);
            diaryElementService.create(diaryId, pageId, userDetails.getId(), element);
        } catch (ResponseStatusException exception) {
            deleteStoredFile(savedImageUrl);
            return redirectWithElementError(exception, diaryId, spread, page, redirectAttributes);
        } catch (RuntimeException exception) {
            deleteStoredFile(savedImageUrl);
            throw exception;
        }
        return redirectToEditPage(diaryId, spread, page);
    }

    /**
     * 사진 요소 삭제. DB 행을 먼저 지우고 실제 업로드 파일을 정리한다.
     * 꾸미던 자리를 잃지 않도록 화면 이동 없이 값만 돌려준다. (지운 요소는 화면에서 JS 가 뺀다)
     */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/photo/delete")
    @ResponseBody
    public ResponseEntity<?> deletePhotoElement(@PathVariable Long diaryId,
                                                @PathVariable Long pageId,
                                                @PathVariable Long elementId,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getId();
        String imageUrl;
        try {
            // 소유권·페이지·요소 소속은 서비스가 확인한다.
            DiaryElement existing = diaryElementService.getElement(diaryId, pageId, elementId, userId);
            if (!PHOTO_ELEMENT_TYPE.equals(existing.getElementType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 요소가 아닙니다.");
            }
            imageUrl = existing.getImageUrl();
            diaryElementService.delete(diaryId, pageId, elementId, userId);
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "사진을 삭제하지 못했습니다.");
        }

        // DB 삭제가 끝난 뒤 실제 파일을 정리한다. (정리 실패는 요청을 깨뜨리지 않는다)
        deleteStoredFile(imageUrl);
        return ResponseEntity.noContent().build();
    }

    /**
     * 페이지에 공용 스티커를 한 장 붙인다.
     * 클라이언트는 스티커 id 만 보내고 실제 경로는 서버가 허용 목록(DiaryStickerCatalog)에서 고른다.
     * 소유권 확인은 기존 요소 생성 흐름(diaryElementService)이 그대로 맡는다.
     */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/sticker")
    @ResponseBody
    public ResponseEntity<?> createStickerElement(@PathVariable Long diaryId,
                                                  @PathVariable Long pageId,
                                                  @RequestParam("sticker") String stickerId,
                                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        DiarySticker sticker = diaryStickerCatalog.find(stickerId).orElse(null);
        if (sticker == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "알 수 없는 스티커입니다."));
        }

        Long userId = userDetails.getId();
        DiaryElement created;
        try {
            // 같은 자리에 겹쳐 쌓이지 않게 이미 붙어 있는 요소 수만큼 조금씩 어긋나게 놓는다.
            int placed = diaryElementService.getElements(diaryId, pageId, userId).size();
            BigDecimal offset = STICKER_OFFSET_STEP
                    .multiply(BigDecimal.valueOf(placed % STICKER_OFFSET_CYCLE));

            DiaryElement element = new DiaryElement();
            element.setElementType(STICKER_ELEMENT_TYPE);
            element.setImageUrl(sticker.imageUrl());
            element.setPositionX(STICKER_CENTER.add(offset));
            element.setPositionY(STICKER_CENTER.add(offset));
            element.setWidth(STICKER_SIZE);
            element.setHeight(STICKER_SIZE);
            // 회전 0 / 겹침 순서는 사진과 같은 기본값을 쓴다.
            created = diaryElementService.create(diaryId, pageId, userId, element);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "스티커를 붙이지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.ok(stickerPayload(diaryId, pageId, created, sticker.name()));
    }

    /**
     * 스티커 요소 삭제.
     * 공용 asset 이므로 DB 행만 지우고 실제 SVG 파일은 건드리지 않는다. (사진 삭제와 다른 점)
     * 사진과 마찬가지로 화면 이동 없이 값만 돌려준다.
     */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/sticker/delete")
    @ResponseBody
    public ResponseEntity<?> deleteStickerElement(@PathVariable Long diaryId,
                                                  @PathVariable Long pageId,
                                                  @PathVariable Long elementId,
                                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getId();
        try {
            // 소유권·페이지·요소 소속은 서비스가 확인한다.
            DiaryElement existing = diaryElementService.getElement(diaryId, pageId, elementId, userId);
            if (!STICKER_ELEMENT_TYPE.equals(existing.getElementType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스티커 요소가 아닙니다.");
            }
            diaryElementService.delete(diaryId, pageId, elementId, userId);
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "스티커를 떼지 못했습니다.");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 요소 조작 요청의 오류 응답.
     * 없는 요소/남의 요소는 그대로 다시 던져 기존 404 정책(정보를 드러내지 않음)을 지킨다.
     */
    private ResponseEntity<?> elementErrorResponse(ResponseStatusException exception,
                                                   String defaultMessage) {
        if (exception.getStatusCode().is4xxClientError()
                && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of("message", exception.getReason() == null
                            ? defaultMessage : exception.getReason()));
        }
        throw exception;
    }

    /** 새로 붙인 스티커를 화면이 바로 그릴 수 있도록 좌표/크기와 저장 주소를 함께 돌려준다. */
    private Map<String, Object> stickerPayload(Long diaryId, Long pageId,
                                               DiaryElement element, String label) {
        String base = "/diaries/" + diaryId + "/pages/" + pageId + "/elements/" + element.getId();
        return Map.of(
                "id", element.getId(),
                "imageUrl", element.getImageUrl(),
                "label", label,
                "positionX", element.getPositionX(),
                "positionY", element.getPositionY(),
                "width", element.getWidth(),
                "height", element.getHeight(),
                "rotation", element.getRotation(),
                "zIndex", element.getZIndex(),
                "urls", Map.of(
                        "position", base + "/position",
                        "size", base + "/size",
                        "rotation", base + "/rotation",
                        "layer", base + "/layer",
                        "delete", base + "/sticker/delete"));
    }

    /** 드래그로 옮긴 위치 저장. 화면 갱신 없이 좌표만 반영한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/position")
    @ResponseBody
    public ResponseEntity<?> moveElement(@PathVariable Long diaryId,
                                         @PathVariable Long pageId,
                                         @PathVariable Long elementId,
                                         @RequestParam BigDecimal positionX,
                                         @RequestParam BigDecimal positionY,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryElementService.move(diaryId, pageId, elementId, userDetails.getId(),
                    positionX, positionY);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "위치를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.noContent().build();
    }

    /** 조절한 크기 저장. 화면 갱신 없이 크기만 반영한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/size")
    @ResponseBody
    public ResponseEntity<?> resizeElement(@PathVariable Long diaryId,
                                           @PathVariable Long pageId,
                                           @PathVariable Long elementId,
                                           @RequestParam BigDecimal width,
                                           @RequestParam BigDecimal height,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryElementService.resize(diaryId, pageId, elementId, userDetails.getId(),
                    width, height);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "크기를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.noContent().build();
    }

    /** 회전한 각도 저장. 화면 갱신 없이 회전값만 반영한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/rotation")
    @ResponseBody
    public ResponseEntity<?> rotateElement(@PathVariable Long diaryId,
                                           @PathVariable Long pageId,
                                           @PathVariable Long elementId,
                                           @RequestParam BigDecimal rotation,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryElementService.rotate(diaryId, pageId, elementId, userDetails.getId(), rotation);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "회전 각도를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }
        return ResponseEntity.noContent().build();
    }

    /** 겹침 순서를 한 단계 앞/뒤로 옮긴다. 새 순서는 서버가 계산한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/layer")
    @ResponseBody
    public ResponseEntity<?> changeElementLayer(@PathVariable Long diaryId,
                                                @PathVariable Long pageId,
                                                @PathVariable Long elementId,
                                                @RequestParam String direction,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean forward = "FORWARD".equalsIgnoreCase(direction);
        if (!forward && !"BACKWARD".equalsIgnoreCase(direction)) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청입니다."));
        }

        List<DiaryElement> ordered;
        try {
            ordered = diaryElementService.changeLayer(
                    diaryId, pageId, elementId, userDetails.getId(), forward);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("message", exception.getReason() == null
                                ? "겹침 순서를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        }

        // 화면이 바로 갱신할 수 있도록 정리된 순서를 돌려준다.
        List<Map<String, Object>> layers = ordered.stream()
                .map(element -> Map.<String, Object>of(
                        "id", element.getId(), "zIndex", element.getZIndex()))
                .toList();
        return ResponseEntity.ok(Map.of("elements", layers));
    }

    /** 펼친 장의 요소를 읽는다. 페이지가 없으면 조회하지 않는다. */
    private List<DiaryElement> pageElements(Long diaryId, DiaryPage page, Long userId) {
        if (page == null) {
            return List.of();
        }
        return diaryElementService.getElements(diaryId, page.getId(), userId);
    }

    /** 작업 후에는 보고 있던 펼침 위치로, 편집 화면 그대로 돌아온다. */
    private String redirectToSpread(Long diaryId, int spread) {
        return redirectToEditPage(diaryId, spread, null);
    }

    /** 편집 모드는 한 장만 보여주므로 편집 중이던 페이지(pageOrder)까지 유지한다. */
    private String redirectToEditPage(Long diaryId, int spread, Integer pageOrder) {
        return "redirect:/diaries/" + diaryId + "?spread=" + Math.max(spread, 0) + "&edit=true"
                + (pageOrder == null ? "" : "&page=" + pageOrder);
    }

    private String redirectWithElementError(ResponseStatusException exception,
                                            Long diaryId,
                                            int spread,
                                            RedirectAttributes redirectAttributes) {
        return redirectWithElementError(exception, diaryId, spread, null, redirectAttributes);
    }

    private String redirectWithElementError(ResponseStatusException exception,
                                            Long diaryId,
                                            int spread,
                                            Integer pageOrder,
                                            RedirectAttributes redirectAttributes) {
        if (exception.getStatusCode().is4xxClientError()
                && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            redirectAttributes.addFlashAttribute("diaryPageError", exception.getReason());
            return redirectToEditPage(diaryId, spread, pageOrder);
        }
        throw exception;
    }

    /** 다이어리 마지막 장 뒤에 새 페이지를 추가한다. (순서는 서비스가 정한다) */
    @PostMapping("/{diaryId:\\d+}/pages")
    public String createPage(@PathVariable Long diaryId,
                             @ModelAttribute DiaryPage pageForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        // 페이지 추가 폼은 읽기 화면 상단에 있으므로 실패하면 읽기 화면으로 되돌아온다.
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "페이지 날짜를 올바르게 선택해 주세요.");
            return "redirect:/diaries/" + diaryId;
        }

        DiaryPage added;
        try {
            DiaryPage page = new DiaryPage();
            page.setPageDate(pageForm.getPageDate());
            page.setBackgroundType(pageForm.getBackgroundType());
            added = diaryPageService.append(diaryId, userDetails.getId(), page);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                redirectAttributes.addFlashAttribute("diaryPageError", exception.getReason());
                return "redirect:/diaries/" + diaryId;
            }
            throw exception;
        }

        redirectAttributes.addFlashAttribute("diaryMessage", "새 페이지가 추가되었습니다.");
        // 편집 모드는 한 장씩 보므로 방금 추가한 장을 바로 열어 준다.
        return "redirect:/diaries/" + diaryId + "?edit=true&page=" + added.getPageOrder();
    }

    /** 새 여행일기 작성 화면 */
    @GetMapping("/new")
    public String newDiaryForm(Model model) {
        model.addAttribute("diaryForm", new Diary());
        model.addAttribute("coverStyles", DiaryCoverStyle.values());
        model.addAttribute("pageTitle", "새 여행일기");
        return "diary/new";
    }

    /** 새 여행일기 저장. 소유자는 요청 값이 아니라 현재 사용자로 설정한다. */
    @PostMapping
    public String createDiary(@ModelAttribute("diaryForm") Diary diaryForm,
                              BindingResult bindingResult,
                              @RequestParam(value = "coverImage", required = false)
                              MultipartFile coverImage,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderNewForm(model, "여행 기간을 올바르게 입력해 주세요.");
        }

        // 이번 요청에서 저장한 대표 이미지만 추적해 실패 시 정리한다.
        String savedCoverImageUrl = null;
        try {
            if (coverImage != null && !coverImage.isEmpty()) {
                savedCoverImageUrl = fileUploadService.saveFile(coverImage, COVER_IMAGE_DIRECTORY);
            }

            // 제목/기간/표지 스타일과 업로드 결과만 사용하고 요청의 다른 값은 신뢰하지 않는다.
            // (표지 스타일 허용 값 확인은 서비스가 한다)
            Diary diary = new Diary();
            diary.setTitle(diaryForm.getTitle());
            diary.setStartDate(diaryForm.getStartDate());
            diary.setEndDate(diaryForm.getEndDate());
            diary.setCoverImageUrl(savedCoverImageUrl);
            diary.setCoverStyle(diaryForm.getCoverStyle());
            diaryService.create(userDetails.getId(), diary);
        } catch (ResponseStatusException exception) {
            deleteStoredFile(savedCoverImageUrl);
            if (exception.getStatusCode().is4xxClientError()) {
                return renderNewForm(model, exception.getReason());
            }
            throw exception;
        } catch (RuntimeException exception) {
            deleteStoredFile(savedCoverImageUrl);
            throw exception;
        }

        redirectAttributes.addFlashAttribute("diaryMessage", "여행일기가 만들어졌습니다.");
        return "redirect:/diaries";
    }

    /** 입력값을 그대로 둔 채 오류 메시지와 함께 작성 화면을 다시 보여준다. */
    private String renderNewForm(Model model, String errorMessage) {
        model.addAttribute("diaryError", errorMessage);
        model.addAttribute("coverStyles", DiaryCoverStyle.values());
        model.addAttribute("pageTitle", "새 여행일기");
        return "diary/new";
    }

    /** 저장에 실패했을 때 이번 요청에서 올라간 대표 이미지만 정리한다. */
    private void deleteStoredFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            String relativePath = imageUrl.replaceFirst("^/uploads/", "");
            Files.deleteIfExists(Paths.get(uploadPath, relativePath));
        } catch (IOException ignored) {
            // 파일 정리 실패는 등록 실패 원인을 덮지 않도록 무시한다.
        }
    }
}
