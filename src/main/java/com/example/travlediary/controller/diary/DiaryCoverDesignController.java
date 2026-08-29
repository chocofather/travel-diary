package com.example.travlediary.controller.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.DiaryCoverMaterial;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerKind;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class DiaryCoverDesignController {

    /** 표지 디자인에 올린 사진을 두는 곳. 페이지 사진(diary-pages)과 섞지 않는다. */
    private static final String COVER_DESIGN_IMAGE_DIRECTORY = "diary-cover-designs";
    private static final String PHOTO_ELEMENT_TYPE = "PHOTO";
    /** 이 서비스가 올린 파일만 가리키는 주소 앞머리. 그 밖의 경로는 지우지 않는다. */
    private static final String UPLOAD_URL_PREFIX = "/uploads/";
    /** 새로 만든 디자인의 이름. 편집 화면에서 바로 고칠 수 있다. */
    private static final String DEFAULT_DESIGN_NAME = "새 표지 디자인";

    private final DiaryCoverDesignService diaryCoverDesignService;
    private final DiaryCoverDesignElementService diaryCoverDesignElementService;
    /** 붙일 수 있는 스티커 목록. 페이지 다꾸와 같은 manifest 를 함께 쓴다. */
    private final DiaryStickerCatalog diaryStickerCatalog;
    private final FileUploadService fileUploadService;

    /** 업로드 폴더의 실제 경로. (application.yml 의 custom.upload-path) */
    @Value("${custom.upload-path}")
    private String uploadPath;

    /**
     * 보관함 목록.
     * 카드마다 완성된 표지를 그대로 줄여 보여 주므로 요소도 함께 읽는다.
     * 카드 수만큼 묻지 않도록 디자인 번호를 모아 한 번에 읽는다.
     */
    @GetMapping
    public String designs(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long userId = userDetails.getId();
        List<DiaryCoverDesign> designs = diaryCoverDesignService.getMyDesigns(userId);
        List<Long> designIds = designs.stream().map(DiaryCoverDesign::getId).toList();

        model.addAttribute("coverDesigns", designs);
        model.addAttribute("coverElementsByDesign",
                diaryCoverDesignElementService.getElementsByDesign(designIds, userId));
        // 마스킹테이프 조각 경로. 편집 화면과 같은 값을 써서 같은 모습으로 그려진다.
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "내 표지 디자인");
        return "diary/cover-designs";
    }

    /**
     * 새 디자인을 하나 만들고 바로 꾸미러 간다.
     *
     * <p>이름과 바탕은 편집 화면에서 그대로 고칠 수 있으므로 중간에 따로 묻지 않는다.
     * 다만 자유배치 요소를 붙이려면 디자인 번호가 먼저 있어야 해서, 기본값으로 한 줄 만든 뒤
     * 그 편집 화면으로 보낸다. (이름 중복은 허용이라 기본 이름이 겹쳐도 괜찮다)
     */
    @PostMapping
    public String createDesign(@AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        DiaryCoverDesign blank = new DiaryCoverDesign();
        blank.setName(DEFAULT_DESIGN_NAME);
        blank.setBaseCoverStyle(DiaryCoverStyle.DEFAULT.getCode());
        // 색은 고르지 않은 채로 둔다. (표지 재질의 원래 색으로 시작한다)
        DiaryCoverDesign created = diaryCoverDesignService.create(userDetails.getId(), blank);

        redirectAttributes.addFlashAttribute("coverDesignMessage",
                "새 표지 디자인이 만들어졌습니다. 이름과 바탕은 여기서 정하면 됩니다.");
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
                savedImageUrl = fileUploadService.saveFile(image, COVER_DESIGN_IMAGE_DIRECTORY);
                DiaryCoverDesignElement element = diaryCoverDesignElementService
                        .createPhoto(designId, userId, savedImageUrl, created.size(), photoStyle);
                created.add(photoPayload(designId, element));
            } catch (ResponseStatusException exception) {
                deleteUploadedFile(savedImageUrl);
                if (created.isEmpty()) {
                    return elementErrorResponse(exception, "사진을 붙이지 못했습니다.");
                }
                break; // 앞서 붙은 사진은 그대로 두고 거기까지만 돌려준다
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
                Map.entry("imageUrl", element.getImageUrl()),
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
     * <p>업로드 폴더 안의 경로(/uploads/...)가 아니면 아무것도 하지 않는다.
     * 스티커 같은 공용 asset 경로(/images/...)가 실수로 넘어와도 파일이 지워지지 않게 하는
     * 방어다. (부르는 쪽에서도 유형으로 한 번 거르지만, 여기서 한 번 더 막는다)
     */
    private void deleteUploadedFile(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return;
        }
        try {
            String relativePath = imageUrl.substring(UPLOAD_URL_PREFIX.length());
            Files.deleteIfExists(Paths.get(uploadPath, relativePath));
        } catch (IOException ignored) {
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
        return "redirect:/diaries/cover-designs";
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
        // 고르는 것은 재질 세 갈래뿐이다. 색은 아래 color picker 가 따로 맡는다.
        model.addAttribute("coverMaterials", DiaryCoverMaterial.values());
        model.addAttribute("coverElements",
                diaryCoverDesignElementService.getElements(designId, userId));
        model.addAttribute("diaryStickerCategories", diaryStickerCatalog.getCategories());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        // 사진 모양 고르기(일반/폴라로이드). 목록을 화면에 적지 않고 여기서 넘긴다.
        model.addAttribute("coverPhotoStyles", DiaryCoverPhotoStyle.values());
        model.addAttribute("coverDesignError", errorMessage);
        model.addAttribute("pageTitle", "표지 디자인 편집");
        return "diary/cover-design-edit";
    }
}
