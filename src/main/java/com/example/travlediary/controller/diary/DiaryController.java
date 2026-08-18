package com.example.travlediary.controller.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryElementService;
import com.example.travlediary.service.diary.DiaryPageService;
import com.example.travlediary.service.diary.DiaryService;
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

    private final DiaryService diaryService;
    private final DiaryPageService diaryPageService;
    private final DiaryElementService diaryElementService;
    private final FileUploadService fileUploadService;

    @Value("${custom.upload-path}")
    private String uploadPath;

    /** 내 여행일기 목록 (본인 다이어리만) */
    @GetMapping
    public String diaryList(@AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        model.addAttribute("diaryList", diaryService.getMyDiaryList(userDetails.getId()));
        model.addAttribute("pageTitle", "나의 여행일기");
        return "diary/list";
    }

    /** 다이어리 한 권 펼쳐보기. 소유권은 서비스에서 확인한다. */
    @GetMapping("/{diaryId:\\d+}")
    public String diaryDetail(@PathVariable Long diaryId,
                              @RequestParam(defaultValue = "0") int spread,
                              @RequestParam(defaultValue = "false") boolean edit,
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
        model.addAttribute("leftElements", pageElements(diaryId, leftPage, userId));
        model.addAttribute("rightElements", pageElements(diaryId, rightPage, userId));
        model.addAttribute("currentSpread", currentSpread);
        model.addAttribute("totalSpreads", totalSpreads);
        model.addAttribute("hasPreviousSpread", currentSpread > 0);
        model.addAttribute("hasNextSpread", currentSpread < totalSpreads - 1);
        // 편집 여부는 화면 상태일 뿐이다. 실제 수정 권한은 각 저장 경로에서 다시 확인한다.
        model.addAttribute("editMode", edit);
        model.addAttribute("pageTitle", diary.getTitle() + " | 나의 여행일기");
        return "diary/detail";
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
        return "redirect:/diaries/" + diaryId;
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
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "페이지 날짜를 올바르게 선택해 주세요.");
            return redirectToSpread(diaryId, spread);
        }

        Long userId = userDetails.getId();
        try {
            DiaryPage existing = diaryPageService.getPage(diaryId, pageId, userId);

            DiaryPage changed = new DiaryPage();
            changed.setPageDate(pageForm.getPageDate());
            changed.setBackgroundType(pageForm.getBackgroundType());
            // 순서는 요청 값을 쓰지 않고 기존 값을 유지한다.
            changed.setPageOrder(existing.getPageOrder());
            diaryPageService.update(diaryId, pageId, userId, changed);
        } catch (ResponseStatusException exception) {
            return redirectWithElementError(exception, diaryId, spread, redirectAttributes);
        }
        return redirectToSpread(diaryId, spread);
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

    /** 페이지에 사진(PHOTO)을 한 장 추가한다. 사진 한 장이 요소 한 행이다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/photo")
    public String createPhotoElement(@PathVariable Long diaryId,
                                     @PathVariable Long pageId,
                                     @RequestParam(value = "image", required = false)
                                     MultipartFile image,
                                     @RequestParam(defaultValue = "0") int spread,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        if (image == null || image.isEmpty()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "사진을 선택해 주세요.");
            return redirectToSpread(diaryId, spread);
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
            return redirectWithElementError(exception, diaryId, spread, redirectAttributes);
        } catch (RuntimeException exception) {
            deleteStoredFile(savedImageUrl);
            throw exception;
        }
        return redirectToSpread(diaryId, spread);
    }

    /** 사진 요소 삭제. DB 행을 먼저 지우고 실제 파일을 정리한다. */
    @PostMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/photo/delete")
    public String deletePhotoElement(@PathVariable Long diaryId,
                                     @PathVariable Long pageId,
                                     @PathVariable Long elementId,
                                     @RequestParam(defaultValue = "0") int spread,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
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
            return redirectWithElementError(exception, diaryId, spread, redirectAttributes);
        }

        // DB 삭제가 끝난 뒤 실제 파일을 정리한다. (정리 실패는 요청을 깨뜨리지 않는다)
        deleteStoredFile(imageUrl);
        return redirectToSpread(diaryId, spread);
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
        return "redirect:/diaries/" + diaryId + "?spread=" + Math.max(spread, 0) + "&edit=true";
    }

    private String redirectWithElementError(ResponseStatusException exception,
                                            Long diaryId,
                                            int spread,
                                            RedirectAttributes redirectAttributes) {
        if (exception.getStatusCode().is4xxClientError()
                && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
            redirectAttributes.addFlashAttribute("diaryPageError", exception.getReason());
            return redirectToSpread(diaryId, spread);
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
        // 페이지 추가는 편집 화면에서만 하므로 편집 화면으로 되돌아온다.
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("diaryPageError", "페이지 날짜를 올바르게 선택해 주세요.");
            return "redirect:/diaries/" + diaryId + "?edit=true";
        }

        try {
            DiaryPage page = new DiaryPage();
            page.setPageDate(pageForm.getPageDate());
            page.setBackgroundType(pageForm.getBackgroundType());
            diaryPageService.append(diaryId, userDetails.getId(), page);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                redirectAttributes.addFlashAttribute("diaryPageError", exception.getReason());
                return "redirect:/diaries/" + diaryId + "?edit=true";
            }
            throw exception;
        }

        redirectAttributes.addFlashAttribute("diaryMessage", "새 페이지가 추가되었습니다.");
        return "redirect:/diaries/" + diaryId + "?edit=true";
    }

    /** 새 여행일기 작성 화면 */
    @GetMapping("/new")
    public String newDiaryForm(Model model) {
        model.addAttribute("diaryForm", new Diary());
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

            // 제목/기간과 업로드 결과만 사용하고 요청의 다른 값은 신뢰하지 않는다.
            Diary diary = new Diary();
            diary.setTitle(diaryForm.getTitle());
            diary.setStartDate(diaryForm.getStartDate());
            diary.setEndDate(diaryForm.getEndDate());
            diary.setCoverImageUrl(savedCoverImageUrl);
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
