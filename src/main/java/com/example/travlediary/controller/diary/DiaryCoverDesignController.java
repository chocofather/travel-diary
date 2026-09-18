package com.example.travlediary.controller.diary;

import com.example.travlediary.dto.DiaryCoverLibraryPhotoSelection;
import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.dto.DiaryCoverLibraryShareForm;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.DiaryCoverMaterial;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.model.DiaryPhotoUrls;
import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerKind;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryCoverLibraryRegistrationService;
import com.example.travlediary.service.diary.DiaryLabelFontCatalog;
import com.example.travlediary.service.diary.DiaryPhotoFrame;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 내 표지 디자인 보관함.
 *
 * <p>여행일기(/diaries/{번호})와 주소가 겹치지 않도록 다이어리 쪽 경로가 숫자로 제한되어 있어
 * /diaries/cover-designs 를 그대로 쓸 수 있다.
 *
 * <p>소유권은 여기서 따로 확인하지 않는다. 모든 호출이 현재 로그인 사용자의 userId 를 함께
 * 넘기고, 서비스가 본인 것만 찾아 준다. (요청에 실려 온 소유자 값은 쓰지 않는다)
 */
@Controller
@RequestMapping("/diaries/cover-designs")
@RequiredArgsConstructor
@Slf4j
public class DiaryCoverDesignController {

    /**
     * 표지 디자인에 올린 사진을 두는 곳. 페이지 사진(diary-pages)과 섞지 않는다.
     * 실제 파일은 공개 업로드 폴더가 아니라 private 저장소에 들어간다.
     */
    private static final String COVER_DESIGN_IMAGE_DIRECTORY =
            DiaryPrivatePhotoStorage.COVER_DESIGN_DIRECTORY;
    private static final String PHOTO_ELEMENT_TYPE = "PHOTO";
    /** 신규 미저장 화면에서 먼저 보여 주는 이름. */
    private static final String DEFAULT_DESIGN_NAME = "새 표지 디자인";

    private final DiaryCoverDesignService diaryCoverDesignService;
    private final DiaryCoverDesignElementService diaryCoverDesignElementService;
    private final DiaryCoverLibraryRegistrationService diaryCoverLibraryRegistrationService;
    /** 붙일 수 있는 스티커 목록. 페이지 다꾸와 같은 manifest 를 함께 쓴다. */
    private final DiaryStickerCatalog diaryStickerCatalog;
    /** 라벨기 글꼴 목록. 이것도 페이지 다꾸와 같은 manifest 를 함께 쓴다. */
    private final DiaryLabelFontCatalog diaryLabelFontCatalog;
    /** 표지 디자인 사진은 공개 업로드 폴더가 아니라 이 private 저장소에 둔다. */
    private final DiaryPrivatePhotoStorage diaryPrivatePhotoStorage;

    /**
     * 예전 보관함 주소. 전용 페이지 대신 나의 여행일기 위 표지 디자인 패널을 연다.
     * (내 보유 디자인 목록은 패널 아래쪽이 라이브러리 화면과 함께 보여 준다)
     * 북마크·다른 화면의 링크가 깨지지 않도록 주소는 남겨 두고, 안내 문구도 함께 옮긴다.
     */
    @GetMapping
    public String designs(Model model, RedirectAttributes redirectAttributes) {
        return DiaryCoverDesignHub.open(model, redirectAttributes);
    }

    /** 저장해 둔 내 표지를 확인하고 사진별 공유 범위를 고르는 화면. */
    @GetMapping("/{designId:\\d+}/library-share")
    public String libraryShareForm(@PathVariable Long designId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        Long userId = userDetails.getId();
        DiaryCoverDesign design = diaryCoverDesignService.getMyDesign(designId, userId);
        // 라이브러리에서 받은 디자인은 공유 화면도 열지 않는다. (등록 서비스도 다시 막는다)
        if (design.getSourceLibraryItemId() != null) {
            redirectAttributes.addFlashAttribute("coverDesignError",
                    DiaryCoverLibraryRegistrationService.LIBRARY_SOURCED_SHARE_MESSAGE);
            return DiaryCoverDesignHub.REDIRECT;
        }
        List<DiaryCoverDesignElement> elements =
                diaryCoverDesignElementService.getElements(designId, userId);

        DiaryCoverLibraryShareForm shareForm =
                (DiaryCoverLibraryShareForm) model.getAttribute("shareForm");
        if (shareForm == null) {
            shareForm = new DiaryCoverLibraryShareForm();
            shareForm.setTitle(design.getName());
            model.addAttribute("shareForm", shareForm);
        }

        List<DiaryCoverDesignElement> photos = elements.stream()
                .filter(element -> PHOTO_ELEMENT_TYPE.equals(element.getElementType()))
                .toList();
        Set<Long> includedPhotoIds = shareForm.getPhotoModes() == null ? Set.of()
                : shareForm.getPhotoModes().entrySet().stream()
                        .filter(entry -> entry.getValue()
                                == DiaryCoverLibraryPhotoShareMode.INCLUDED)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

        model.addAttribute("coverDesign", design);
        model.addAttribute("coverElements", elements);
        model.addAttribute("photoElements", photos);
        model.addAttribute("includedPhotoIds", includedPhotoIds);
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "표지 라이브러리에 공유");
        return "diary/cover-library-share";
    }

    /** 화면 입력을 기존 snapshot 등록 서비스의 요청으로만 변환한다. */
    @PostMapping("/{designId:\\d+}/library-share")
    public String shareToLibrary(@PathVariable Long designId,
                                 @ModelAttribute("shareForm")
                                 DiaryCoverLibraryShareForm shareForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return redirectLibraryShareError(designId, shareForm, redirectAttributes,
                    "사진 공유 방식을 다시 선택해 주세요.");
        }

        Map<Long, DiaryCoverLibraryPhotoSelection> photoSelections = new LinkedHashMap<>();
        if (shareForm.getPhotoModes() != null) {
            shareForm.getPhotoModes().forEach((elementId, mode) -> photoSelections.put(
                    elementId,
                    new DiaryCoverLibraryPhotoSelection(mode, shareForm.isRightsConfirmed())));
        }
        DiaryCoverLibraryRegistrationRequest request =
                new DiaryCoverLibraryRegistrationRequest(
                        designId, shareForm.getTitle(), shareForm.getDescription(), photoSelections);

        try {
            diaryCoverLibraryRegistrationService.register(userDetails.getId(), request);
        } catch (ResponseStatusException exception) {
            if (HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                redirectAttributes.addFlashAttribute("coverDesignError",
                        "표지 디자인을 찾을 수 없거나 공유 권한이 없습니다.");
                return DiaryCoverDesignHub.REDIRECT;
            }
            // 라이브러리에서 받은 디자인. 공유 화면으로 돌려보내도 다시 막히므로 표지 디자인 화면으로 보낸다.
            if (HttpStatus.CONFLICT.equals(exception.getStatusCode())) {
                redirectAttributes.addFlashAttribute("coverDesignError",
                        DiaryCoverLibraryRegistrationService.LIBRARY_SOURCED_SHARE_MESSAGE);
                return DiaryCoverDesignHub.REDIRECT;
            }
            if (exception.getStatusCode().is4xxClientError()) {
                String message = exception.getReason() == null
                        ? "공유 설정을 다시 확인해 주세요." : exception.getReason();
                return redirectLibraryShareError(
                        designId, shareForm, redirectAttributes, message);
            }
            log.error("표지 라이브러리 공유 등록에 실패했습니다. designId={}",
                    designId, exception);
            return redirectLibraryShareError(designId, shareForm, redirectAttributes,
                    "표지 디자인을 공유하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RuntimeException exception) {
            log.error("표지 라이브러리 공유 등록에 실패했습니다. designId={}",
                    designId, exception);
            return redirectLibraryShareError(designId, shareForm, redirectAttributes,
                    "표지 디자인을 공유하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        redirectAttributes.addFlashAttribute("coverDesignMessage",
                "표지 디자인을 라이브러리에 공유했습니다.");
        return DiaryCoverDesignHub.REDIRECT;
    }

    private String redirectLibraryShareError(
            Long designId, DiaryCoverLibraryShareForm shareForm,
            RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("shareForm", shareForm);
        redirectAttributes.addFlashAttribute("coverDesignError", message);
        return "redirect:/diaries/cover-designs/" + designId + "/library-share";
    }

    /** 새 여행일기 화면에서 복귀했을 때 내 디자인 선택 목록만 다시 그린다. */
    @GetMapping("/choices")
    public String designChoices(@AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {
        Long userId = userDetails.getId();
        List<DiaryCoverDesign> designs = diaryCoverDesignService.getMyDesigns(userId);
        List<Long> designIds = designs.stream().map(DiaryCoverDesign::getId).toList();

        model.addAttribute("coverDesigns", designs);
        model.addAttribute("coverElementsByDesign",
                diaryCoverDesignElementService.getElementsByDesign(designIds, userId));
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        return "diary/cover-design-choices :: choices";
    }

    /** 새 디자인의 미저장 편집 화면. 이 단계에서는 디자인 행을 만들지 않는다. */
    @GetMapping("/new")
    public String newDesignForm(Model model) {
        DiaryCoverDesign draft = new DiaryCoverDesign();
        draft.setName(DEFAULT_DESIGN_NAME);
        draft.setBaseCoverStyle(DiaryCoverStyle.DEFAULT.getCode());
        return renderNewForm(model, draft, null);
    }

    /** 디자인 저장을 눌렀을 때 처음 행을 만들고, 저장된 편집 화면으로 이동한다. */
    @PostMapping
    public String createDesign(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @ModelAttribute("coverDesign") DiaryCoverDesign coverDesign,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        DiaryCoverDesign created;
        try {
            created = diaryCoverDesignService.create(userDetails.getId(), coverDesign);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return renderNewForm(model, coverDesign, exception.getReason());
            }
            throw exception;
        }

        redirectAttributes.addFlashAttribute("coverDesignMessage",
                "표지 디자인을 저장했습니다. 이제 사진과 스티커를 꾸밀 수 있어요.");
        return "redirect:/diaries/cover-designs/" + created.getId() + "/edit";
    }

    /** 디자인 편집 화면. 표지 미리보기가 곧 자유배치 캔버스다. */
    @GetMapping("/{designId:\\d+}/edit")
    public String editDesignForm(@PathVariable Long designId,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        Long userId = userDetails.getId();
        DiaryCoverDesign design = diaryCoverDesignService.getMyDesign(designId, userId);
        return renderEditForm(model, designId, userId, design, null);
    }

    /**
     * 표지에 공용 스티커를 한 장 붙인다.
     * 클라이언트는 스티커 id 만 보내고 실제 경로는 서버가 허용 목록에서 고른다.
     * (페이지 다꾸의 스티커 붙이기와 같은 방식이고, 화면 이동 없이 값만 돌려준다)
     */
    @PostMapping("/{designId:\\d+}/elements/sticker")
    @ResponseBody
    public ResponseEntity<?> createStickerElement(@PathVariable Long designId,
                                                  @RequestParam("sticker") String stickerId,
                                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        DiarySticker sticker = diaryStickerCatalog.find(stickerId).orElse(null);
        if (sticker == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "알 수 없는 스티커입니다."));
        }
        try {
            DiaryCoverDesignElement created = diaryCoverDesignElementService
                    .createSticker(designId, userDetails.getId(), stickerId);
            return ResponseEntity.ok(stickerPayload(designId, created, sticker));
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "스티커를 붙이지 못했습니다.");
        }
    }

    /**
     * 표지에 사진을 붙인다. 한 번에 여러 장을 고를 수 있고, 사진 한 장이 요소 한 행이다.
     *
     * <p>사진의 모습은 어느 자리에서 올렸는지로 정해진다. 일반 사진과 폴라로이드가 서로 다른
     * 등록 자리를 쓰고, 여기서는 그 값을 함께 받는다. (붙인 뒤에 다시 고르지 않는다)
     *
     * <p>올린 파일은 페이지 사진과 섞이지 않게 표지 디자인 전용 폴더에 둔다.
     * 한 장이라도 실패하면 그 장에서 방금 저장한 파일만 지우고, 앞서 성공한 장은 그대로 둔다.
     * (여러 장을 올리다 한 장이 틀렸다고 이미 붙은 사진까지 되돌리지는 않는다)
     */
    @PostMapping("/{designId:\\d+}/elements/photo")
    @ResponseBody
    public ResponseEntity<?> createPhotoElements(@PathVariable Long designId,
                                                 @RequestParam(value = "images", required = false)
                                                 List<MultipartFile> images,
                                                 @RequestParam String photoStyle,
                                                 @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<MultipartFile> chosen = images == null ? List.of()
                : images.stream().filter(image -> image != null && !image.isEmpty()).toList();
        if (chosen.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "사진을 선택해 주세요."));
        }

        Long userId = userDetails.getId();
        List<Map<String, Object>> created = new ArrayList<>();
        for (MultipartFile image : chosen) {
            // 이번 장에서 저장한 파일만 추적해 실패 시 정리한다. (기존 사진 업로드와 같은 방식)
            String savedImageUrl = null;
            try {
                savedImageUrl = diaryPrivatePhotoStorage.save(image, COVER_DESIGN_IMAGE_DIRECTORY);
                // 폴라로이드의 처음 상자 비율은 사진 원본 비율에서 나온다. (가로 사진 → 가로 폴라로이드)
                DiaryCoverDesignElement element = diaryCoverDesignElementService
                        .createPhoto(designId, userId, savedImageUrl, created.size(), photoStyle,
                                DiaryPhotoFrame.ratioOf(image));
                created.add(photoPayload(designId, element));
            } catch (ResponseStatusException exception) {
                deleteUploadedFile(savedImageUrl);
                if (created.isEmpty()) {
                    return elementErrorResponse(exception, "사진을 붙이지 못했습니다.");
                }
                break; // 앞서 붙은 사진은 그대로 두고 거기까지만 돌려준다
            } catch (UnsupportedImageFormatException exception) {
                // 실제 JPEG/PNG/WEBP 가 아니면 저장하지 않는다. 다른 요소 오류와 같은 규칙으로 알린다.
                if (created.isEmpty()) {
                    return elementErrorResponse(
                            new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()),
                            "사진을 붙이지 못했습니다.");
                }
                break;
            } catch (RuntimeException exception) {
                deleteUploadedFile(savedImageUrl);
                throw exception;
            }
        }
        return ResponseEntity.ok(Map.of("photos", created));
    }

    /**
     * 사진 요소 삭제.
     * DB 행을 먼저 지우고 실제 업로드 파일을 정리한다. (기존 페이지 사진 삭제와 같은 순서)
     */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/photo/delete")
    @ResponseBody
    public ResponseEntity<?> deletePhotoElement(@PathVariable Long designId,
                                                @PathVariable Long elementId,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        DiaryCoverDesignElement removed;
        try {
            removed = diaryCoverDesignElementService.delete(designId, elementId, userDetails.getId());
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "사진을 지우지 못했습니다.");
        }

        // 사진일 때만 올린 파일을 정리한다. 스티커는 공용 asset 이라 파일을 건드리지 않는다.
        if (PHOTO_ELEMENT_TYPE.equals(removed.getElementType())) {
            deleteUploadedFile(removed.getImageUrl());
        }
        return ResponseEntity.noContent().build();
    }

    /** 드래그로 옮긴 자리 저장 */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/position")
    @ResponseBody
    public ResponseEntity<?> moveElement(@PathVariable Long designId,
                                         @PathVariable Long elementId,
                                         @RequestParam BigDecimal positionX,
                                         @RequestParam BigDecimal positionY,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryCoverDesignElementService.move(designId, elementId, userDetails.getId(),
                    positionX, positionY);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "위치를 저장하지 못했습니다.");
        }
    }

    /** 크기 저장 */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/size")
    @ResponseBody
    public ResponseEntity<?> resizeElement(@PathVariable Long designId,
                                           @PathVariable Long elementId,
                                           @RequestParam BigDecimal width,
                                           @RequestParam BigDecimal height,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryCoverDesignElementService.resize(designId, elementId, userDetails.getId(),
                    width, height);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "크기를 저장하지 못했습니다.");
        }
    }

    /** 회전 각도 저장 */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/rotation")
    @ResponseBody
    public ResponseEntity<?> rotateElement(@PathVariable Long designId,
                                           @PathVariable Long elementId,
                                           @RequestParam BigDecimal rotation,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryCoverDesignElementService.rotate(designId, elementId, userDetails.getId(), rotation);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "회전을 저장하지 못했습니다.");
        }
    }

    /**
     * 사진의 모습 바꾸기 (일반 / 폴라로이드).
     * 그 칸 하나만 바꾸므로 자리/크기/각도/겹침 순서는 그대로 남는다.
     */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/photo-style")
    @ResponseBody
    public ResponseEntity<?> changePhotoStyle(@PathVariable Long designId,
                                              @PathVariable Long elementId,
                                              @RequestParam String photoStyle,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            DiaryCoverDesignElement changed = diaryCoverDesignElementService
                    .changePhotoStyle(designId, elementId, userDetails.getId(), photoStyle);
            return ResponseEntity.ok(Map.of(
                    "photoStyle", changed.getPhotoStyleCode(),
                    "photoStyleClass", changed.getPhotoStyleClass()));
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "사진 모양을 바꾸지 못했습니다.");
        }
    }

    /** 겹침 순서 한 칸 이동. 정리된 전체 순서를 돌려준다. */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/layer")
    @ResponseBody
    public ResponseEntity<?> changeElementLayer(@PathVariable Long designId,
                                                @PathVariable Long elementId,
                                                @RequestParam String direction,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean forward = "FORWARD".equalsIgnoreCase(direction);
        if (!forward && !"BACKWARD".equalsIgnoreCase(direction)) {
            return ResponseEntity.badRequest().body(Map.of("message", "겹침 순서를 바꾸지 못했습니다."));
        }
        try {
            List<DiaryCoverDesignElement> ordered = diaryCoverDesignElementService
                    .changeLayer(designId, elementId, userDetails.getId(), forward);
            List<Map<String, Object>> layers = ordered.stream()
                    .map(element -> Map.<String, Object>of(
                            "id", element.getId(), "zIndex", element.getZIndex()))
                    .toList();
            return ResponseEntity.ok(Map.of("elements", layers));
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "겹침 순서를 바꾸지 못했습니다.");
        }
    }

    /**
     * 스티커 떼기.
     * 공용 asset 이므로 DB 행만 지우고 실제 그림 파일은 건드리지 않는다.
     */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/sticker/delete")
    @ResponseBody
    public ResponseEntity<?> deleteStickerElement(@PathVariable Long designId,
                                                  @PathVariable Long elementId,
                                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryCoverDesignElementService.delete(designId, elementId, userDetails.getId());
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "스티커를 떼지 못했습니다.");
        }
    }

    /**
     * 라벨기로 표지에 글씨를 붙인다.
     * 페이지 다꾸의 라벨기와 같은 규칙이고, 화면 이동 없이 값만 돌려준다.
     * (문구 다듬기·글꼴 허용 검사·자리/크기는 모두 서비스가 정한다)
     */
    @PostMapping("/{designId:\\d+}/elements/label")
    @ResponseBody
    public ResponseEntity<?> createLabelElement(@PathVariable Long designId,
                                                @RequestParam("text") String text,
                                                @RequestParam(name = "textFont", required = false)
                                                String textFont,
                                                @RequestParam(name = "textColor", required = false)
                                                String textColor,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            DiaryCoverDesignElement created = diaryCoverDesignElementService
                    .createLabel(designId, userDetails.getId(), text, textFont, textColor);
            return ResponseEntity.ok(labelPayload(designId, created));
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "글씨를 붙이지 못했습니다.");
        }
    }

    /**
     * 글씨 떼기.
     * 파일을 갖지 않으므로 DB 행만 지운다. (사진 삭제와 다른 점)
     */
    @PostMapping("/{designId:\\d+}/elements/{elementId:\\d+}/label/delete")
    @ResponseBody
    public ResponseEntity<?> deleteLabelElement(@PathVariable Long designId,
                                                @PathVariable Long elementId,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            diaryCoverDesignElementService.deleteLabel(designId, elementId, userDetails.getId());
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException exception) {
            return elementErrorResponse(exception, "글씨를 떼지 못했습니다.");
        }
    }

    /**
     * 방금 붙인 글씨를 화면이 그대로 그릴 수 있도록 값과 저장 주소를 함께 돌려준다.
     * 글꼴 class 도 함께 준다 — 화면이 code 를 다시 class 로 바꾸는 규칙을 갖지 않게 한다.
     */
    private Map<String, Object> labelPayload(Long designId, DiaryCoverDesignElement element) {
        String base = "/diaries/cover-designs/" + designId + "/elements/" + element.getId();
        return Map.ofEntries(
                Map.entry("id", element.getId()),
                Map.entry("elementType", element.getElementType()),
                Map.entry("textContent", element.getTextContent()),
                // 글꼴을 고르지 않은 글씨도 있어 빈 문자열로 내려 준다.
                Map.entry("textFont", element.getTextFont() == null
                        ? "" : element.getTextFont()),
                Map.entry("fontClass", element.getTextFontClass() == null
                        ? "" : element.getTextFontClass()),
                // 글자색을 고르지 않은 글씨도 있어 빈 문자열로 내려 준다.
                Map.entry("textColor", element.getTextColor() == null
                        ? "" : element.getTextColor()),
                Map.entry("positionX", element.getPositionX()),
                Map.entry("positionY", element.getPositionY()),
                Map.entry("width", element.getWidth()),
                Map.entry("height", element.getHeight()),
                Map.entry("rotation", element.getRotation()),
                Map.entry("zIndex", element.getZIndex()),
                Map.entry("urls", Map.of(
                        "position", base + "/position",
                        "size", base + "/size",
                        "rotation", base + "/rotation",
                        "layer", base + "/layer",
                        "delete", base + "/label/delete")));
    }

    /** 방금 붙인 스티커를 화면이 그대로 그릴 수 있도록 값과 저장 주소를 함께 돌려준다. */
    private Map<String, Object> stickerPayload(Long designId, DiaryCoverDesignElement element,
                                               DiarySticker sticker) {
        String base = "/diaries/cover-designs/" + designId + "/elements/" + element.getId();
        Map<String, String> repeat = sticker.isRepeating()
                ? Map.of("left", sticker.repeat().leftUrl(),
                         "center", sticker.repeat().centerUrl(),
                         "right", sticker.repeat().rightUrl())
                : Map.of();
        return Map.ofEntries(
                Map.entry("id", element.getId()),
                Map.entry("imageUrl", element.getImageUrl()),
                Map.entry("label", sticker.name()),
                Map.entry("repeat", repeat),
                Map.entry("maskingTape", DiaryStickerKind.isMaskingTape(element.getImageUrl())),
                Map.entry("positionX", element.getPositionX()),
                Map.entry("positionY", element.getPositionY()),
                Map.entry("width", element.getWidth()),
                Map.entry("height", element.getHeight()),
                Map.entry("rotation", element.getRotation()),
                Map.entry("zIndex", element.getZIndex()),
                Map.entry("urls", Map.of(
                        "position", base + "/position",
                        "size", base + "/size",
                        "rotation", base + "/rotation",
                        "layer", base + "/layer",
                        "delete", base + "/sticker/delete")));
    }

    /** 방금 붙인 사진을 화면이 그대로 그릴 수 있도록 값과 저장 주소를 함께 돌려준다. */
    private Map<String, Object> photoPayload(Long designId, DiaryCoverDesignElement element) {
        String base = "/diaries/cover-designs/" + designId + "/elements/" + element.getId();
        return Map.ofEntries(
                Map.entry("id", element.getId()),
                // 저장 키가 아니라 통제된 주소를 내려 준다. (화면은 이 주소로만 사진을 연다)
                Map.entry("imageUrl",
                        DiaryPhotoUrls.coverDesignElementPhoto(designId, element.getId())),
                // 어떤 모습으로 붙었는지. (등록한 자리가 정한 값을 그대로 알려 준다)
                Map.entry("photoStyle", element.getPhotoStyleCode()),
                Map.entry("photoStyleClass", element.getPhotoStyleClass()),
                Map.entry("positionX", element.getPositionX()),
                Map.entry("positionY", element.getPositionY()),
                Map.entry("width", element.getWidth()),
                Map.entry("height", element.getHeight()),
                Map.entry("rotation", element.getRotation()),
                Map.entry("zIndex", element.getZIndex()),
                Map.entry("urls", Map.of(
                        "position", base + "/position",
                        "size", base + "/size",
                        "rotation", base + "/rotation",
                        "layer", base + "/layer",
                        "photoStyle", base + "/photo-style",
                        "delete", base + "/photo/delete")));
    }

    /**
     * 이 화면에서 올린 파일만 지운다.
     *
     * <p>관리 대상 저장 키가 아니면 저장소가 스스로 아무것도 하지 않는다. 스티커 같은 공용
     * asset 경로(/images/...)가 실수로 넘어와도 파일이 지워지지 않게 하는 방어다.
     * (부르는 쪽에서도 유형으로 한 번 거르지만, 저장소에서 한 번 더 막힌다)
     */
    private void deleteUploadedFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            diaryPrivatePhotoStorage.delete(imageUrl);
        } catch (RuntimeException ignored) {
            // 파일 정리 실패는 삭제 요청을 깨뜨리지 않는다.
        }
    }

    /** 요소 조작 실패 응답. 400 계열은 이유를 그대로 알려 주고 그 밖은 그대로 올린다. */
    private ResponseEntity<?> elementErrorResponse(ResponseStatusException exception,
                                                   String fallbackMessage) {
        if (!exception.getStatusCode().is4xxClientError()) {
            throw exception;
        }
        String message = exception.getReason() == null ? fallbackMessage : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    /** 기본 정보(이름 / 바탕 표지 / 바탕색) 저장 */
    @PostMapping("/{designId:\\d+}/update")
    public String updateDesign(@PathVariable Long designId,
                               @ModelAttribute("coverDesign") DiaryCoverDesign coverDesign,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        try {
            diaryCoverDesignService.updateBasics(designId, userDetails.getId(),
                    coverDesign.getName(), coverDesign.getBaseCoverStyle(),
                    coverDesign.getBackgroundColor());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    && !HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                return renderEditForm(model, designId, userDetails.getId(),
                        coverDesign, exception.getReason());
            }
            throw exception;
        }

        redirectAttributes.addFlashAttribute("coverDesignMessage", "표지 디자인을 저장했습니다.");
        return "redirect:/diaries/cover-designs/" + designId + "/edit";
    }

    /**
     * 디자인 삭제.
     * 아직 사진 요소가 없으므로 지울 파일도 없다. (서비스가 돌려주는 요소 목록은 다음 단계에서 쓴다)
     * 이미 다이어리에 적용된 표지는 값을 복사해 둔 별개의 행이라 그대로 남는다.
     */
    @PostMapping("/{designId:\\d+}/delete")
    public String deleteDesign(@PathVariable Long designId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        diaryCoverDesignService.delete(designId, userDetails.getId());
        redirectAttributes.addFlashAttribute("coverDesignMessage", "표지 디자인을 삭제했습니다.");
        return DiaryCoverDesignHub.REDIRECT;
    }

    /**
     * 입력값을 그대로 둔 채 오류 메시지와 함께 편집 화면을 다시 보여준다.
     *
     * <p>표지 위에 그릴 요소는 겹침 순서 그대로 싣고, 스티커 목록과 되풀이 조각은
     * 페이지 다꾸와 같은 manifest 값을 그대로 쓴다.
     */
    private String renderEditForm(Model model, Long designId, Long userId,
                                  DiaryCoverDesign coverDesign, String errorMessage) {
        model.addAttribute("coverDesign", coverDesign);
        model.addAttribute("designId", designId);
        model.addAttribute("newDesign", false);
        // 고르는 것은 재질 세 갈래뿐이다. 색은 아래 color picker 가 따로 맡는다.
        model.addAttribute("coverMaterials", DiaryCoverMaterial.values());
        model.addAttribute("coverElements",
                diaryCoverDesignElementService.getElements(designId, userId));
        model.addAttribute("diaryStickerCategories", diaryStickerCatalog.getCategories());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        // 사진 모양 고르기(일반/폴라로이드). 목록을 화면에 적지 않고 여기서 넘긴다.
        model.addAttribute("coverPhotoStyles", DiaryCoverPhotoStyle.values());
        // 라벨기 글꼴. 페이지 다꾸와 같은 manifest 를 그대로 내려 준다.
        model.addAttribute("diaryLabelFonts", diaryLabelFontCatalog.getFonts());
        model.addAttribute("coverDesignError", errorMessage);
        model.addAttribute("pageTitle", "표지 디자인 편집");
        return "diary/cover-design-edit";
    }

    /** 신규 기본정보 폼. 자유배치 요소는 디자인 번호가 생긴 뒤 기존 편집 화면에서 붙인다. */
    private String renderNewForm(Model model, DiaryCoverDesign coverDesign, String errorMessage) {
        model.addAttribute("coverDesign", coverDesign);
        model.addAttribute("newDesign", true);
        model.addAttribute("coverMaterials", DiaryCoverMaterial.values());
        model.addAttribute("coverElements", List.of());
        model.addAttribute("coverDesignError", errorMessage);
        model.addAttribute("pageTitle", "새 표지 디자인");
        return "diary/cover-design-edit";
    }
}
