package com.tripbora.controller.admin;

import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageLicenseType;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.service.destination.DestinationCommonsImageManagementService;
import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.destination.DestinationKtoImageManagementService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.file.DestinationCardThumbnailService;
import com.tripbora.service.file.UnsupportedImageFormatException;
import com.tripbora.service.kto.InvalidKtoSelectedPhotosException;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
import com.tripbora.service.wikidata.CommonsApiException;
import com.tripbora.service.wikidata.CommonsPhotoDownloadException;
import com.tripbora.service.wikidata.WikidataApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/destinations")
public class AdminDestinationImageController {

    private final DestinationImageService destinationImageService;
    private final DestinationService destinationService;
    private final KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser;
    private final DestinationKtoImageManagementService ktoImageManagementService;
    private final DestinationCommonsImageManagementService commonsImageManagementService;
    private final DestinationCardThumbnailService cardThumbnailService;

    /** 사진 한 장과 함께 가는 출처 입력값·multipart 머리말 여유. */
    static final long UPLOAD_REQUEST_OVERHEAD_BYTES = 64L * 1024;

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    @Value("${spring.servlet.multipart.max-request-size:25MB}")
    private DataSize maxRequestSize = DataSize.ofMegabytes(25);

    @GetMapping("/{id}/images")
    public String showImageUploadForm(@PathVariable Long id, Model model) {
        List<DestinationImage> images = destinationImageService.getImages(id);
        model.addAttribute("destinationId", id);
        // 직접 업로드는 사진을 한 장씩 보낸다. 한 장이 요청 한도 안에 들어야 하므로 이 크기를 넘는 사진은 미리 알린다.
        model.addAttribute("imageUploadMaxBytes", Math.min(maxFileSize.toBytes(),
                maxRequestSize.toBytes() - UPLOAD_REQUEST_OVERHEAD_BYTES));
        model.addAttribute("destinationName", destinationName(id));
        model.addAttribute("imageList", images);
        /*
          등록된 사진 카드와 순서 편집 격자는 작은 칸이라 원본(수천 px) 대신 여행지 카드 썸네일(작은 크기)을 쓴다.
          원본을 쓰면 38장만 돼도 100MB 넘게 받고, 사진을 옮길 때마다 큰 그림을 다시 펼치느라 화면이 멈췄다.
          썸네일을 만들 수 없는 주소(외부 이미지 등)는 원본을 그대로 쓴다.
          이미 만들어 둔 썸네일만 쓴다. 아직 없는 사진은 원본으로 바로 보여 주고 다음 방문부터 썸네일을 쓴다.
          관리 화면이 썸네일 만들기를 기다리게 하면 첫 화면이 늦고, 그 요청이 공개 화면의 썸네일 요청 앞에 줄을 선다.
        */
        // 공공누리 제3유형(변경금지)은 예전에 만들어 둔 썸네일이 있어도 쓰지 않고 원본을 그대로 보여 준다.
        Map<Long, String> imageThumbnails = new HashMap<>();
        images.stream().filter(image -> !image.isNoDerivatives())
                .forEach(image -> cardThumbnailService.cardImage(image.getImageUrl())
                .filter(card -> cardThumbnailService.isSmallThumbnailReady(image.getImageUrl()))
                .ifPresent(card -> imageThumbnails.put(image.getId(), card.src())));
        model.addAttribute("imageThumbnails", imageThumbnails);
        // 아직 없는 썸네일은 화면 순서대로 뒤에서 미리 만든다(응답은 기다리지 않는다).
        cardThumbnailService.prewarm(images.stream().map(DestinationImage::getImageUrl).toList());
        model.addAttribute("imageCount", images.size());
        // Wikidata 여행지에만 Commons 사진 추가를 보여준다. 이미 있는 파일은 후보에서 선택할 수 없게 표시한다.
        model.addAttribute("wikidataQid", commonsImageManagementService.findWikidataQid(id));
        model.addAttribute("registeredCommonsFiles", commonsImageManagementService.registeredCommonsFileNames(images));
        model.addAttribute("imageLicenseOptions", DestinationImageLicenseType.values());
        model.addAttribute("imageLicenseCodes", Arrays.stream(DestinationImageLicenseType.values())
                .map(DestinationImageLicenseType::getCode)
                .toList());
        return "admin/destinations/image-upload";
    }

    public String uploadImages(Long id,
                               MultipartFile[] files,
                               Model model,
                               HttpServletResponse response) {
        return uploadImages(id, files, null, null, null, null, null, null, null, model, response);
    }

    public String uploadImages(Long id, MultipartFile[] files, String[] sourceNames,
                               String[] photographers, String[] licenseTypes,
                               String[] licenseDetails, String[] sourceUrls,
                               Model model, HttpServletResponse response) {
        return uploadImages(id, files, sourceNames, photographers, licenseTypes,
                licenseDetails, sourceUrls, null, null, model, response);
    }

    @PostMapping("/{id}/images")
    public String uploadImages(@PathVariable Long id,
                               @RequestParam("files") MultipartFile[] files,
                               @RequestParam(value = "imageSourceNames", required = false)
                               String[] sourceNames,
                               @RequestParam(value = "imagePhotographers", required = false)
                               String[] photographers,
                               @RequestParam(value = "imageLicenseTypes", required = false)
                               String[] licenseTypes,
                               @RequestParam(value = "imageLicenseDetails", required = false)
                               String[] licenseDetails,
                               @RequestParam(value = "imageSourceUrls", required = false)
                               String[] sourceUrls,
                               @RequestParam(value = "imageCommonSourceUrls", required = false)
                               String[] commonSourceUrls,
                               @RequestParam(value = "imageWorkPageUrls", required = false)
                               String[] workPageUrls,
                               Model model,
                               HttpServletResponse response) {
        try {
            if (sourceNames == null && photographers == null && licenseTypes == null
                    && licenseDetails == null && sourceUrls == null
                    && commonSourceUrls == null && workPageUrls == null) {
                destinationImageService.saveImages(id, files, null, new Integer[0]);
            } else {
                destinationImageService.saveImages(
                        id, files, null, new Integer[0],
                        sourceNames, photographers, licenseTypes, licenseDetails, sourceUrls,
                        commonSourceUrls, workPageUrls);
            }
        } catch (UnsupportedImageFormatException | DestinationImageService.InvalidSourceUrlException exception) {
            // 잘못된 이미지는 입력 오류이므로 400 을 유지하되 관리 화면 안에서 이유를 보여준다
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("imageError", exception.getMessage());
            return showImageUploadForm(id, model);
        }
        return managementRedirect(id);
    }

    @PostMapping("/{id}/images/kto")
    public String addKtoPhotos(@PathVariable Long id,
                               @RequestParam(value = "ktoSelectedPhotosJson", required = false)
                               String selectedPhotosJson) {
        try {
            List<KtoSelectedPhotoRequest> selectedPhotos =
                    ktoSelectedPhotoRequestParser.parse(selectedPhotosJson);
            ktoImageManagementService.addPhotos(id, selectedPhotos);
        } catch (InvalidKtoSelectedPhotosException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 관광사진 정보가 올바르지 않습니다.");
        }
        return managementRedirect(id);
    }

    /** 기존 Wikidata 여행지에 Commons 사진을 더한다. 결과는 관리 화면으로 돌아가 안내한다. */
    @PostMapping("/{id}/images/commons")
    public String addCommonsPhotos(@PathVariable Long id,
                                   @RequestParam(value = "commonsSelectedPhotosJson", required = false)
                                   String selectedPhotosJson,
                                   RedirectAttributes redirectAttributes) {
        try {
            int added = commonsImageManagementService.addPhotos(id, selectedPhotosJson);
            redirectAttributes.addFlashAttribute("commonsAddResult", "Commons 사진 " + added + "장을 추가했습니다.");
        } catch (IllegalArgumentException | NoSuchElementException | WikidataApiException | CommonsApiException
                 | CommonsPhotoDownloadException exception) {
            redirectAttributes.addFlashAttribute("commonsAddError", exception.getMessage());
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("commonsAddError",
                    "Commons 사진 저장에 실패했습니다. 선택한 사진은 하나도 저장되지 않았습니다. 다시 시도해 주세요.");
        }
        return managementRedirect(id) + "#commons-add";
    }

    @PostMapping("/images/{imageId}/main")
    public String setMainImage(@RequestParam("destinationId") Long destinationId,
                               @PathVariable Long imageId) {
        try {
            destinationImageService.setMainImage(destinationId, imageId);
        } catch (IllegalArgumentException exception) {
            throw invalidImageRequest();
        }
        return managementRedirect(destinationId);
    }

    @PostMapping("/images/{imageId}/slide")
    public String toggleSlideImage(@RequestParam("destinationId") Long destinationId,
                                   @PathVariable Long imageId) {
        try {
            destinationImageService.toggleSlideImage(destinationId, imageId);
        } catch (IllegalArgumentException exception) {
            throw invalidImageRequest();
        }
        return managementRedirect(destinationId);
    }

    @PostMapping("/images/{imageId}/metadata")
    public String updateImageMetadata(@RequestParam("destinationId") Long destinationId,
                                      @PathVariable Long imageId,
                                      @RequestParam(value = "sourceName", required = false)
                                      String sourceName,
                                      @RequestParam(value = "photographer", required = false)
                                      String photographer,
                                      @RequestParam(value = "licenseType", required = false)
                                      String licenseType,
                                      @RequestParam(value = "licenseDetail", required = false)
                                      String licenseDetail,
                                      @RequestParam(value = "sourceUrl", required = false)
                                      String sourceUrl,
                                      @RequestParam(value = "commonSourceUrl", required = false)
                                      String commonSourceUrl,
                                      @RequestParam(value = "workPageUrl", required = false)
                                      String workPageUrl,
                                      RedirectAttributes redirectAttributes) {
        try {
            destinationImageService.updateImageMetadataAndPages(
                    destinationId, imageId, sourceName, photographer,
                    licenseType, licenseDetail, sourceUrl, commonSourceUrl, workPageUrl);
        } catch (DestinationImageService.InvalidSourceUrlException exception) {
            /*
              입력값 문제는 오류 페이지로 보내지 않는다. 관리 화면의 그 사진 카드로 돌아가 출처 영역을 펼치고,
              이유와 방금 입력한 값을 그대로 보여 준다. (없는·다른 여행지 사진은 아래처럼 400)
            */
            Map<String, String> draft = new HashMap<>();
            draft.put("sourceName", sourceName);
            draft.put("photographer", photographer);
            draft.put("licenseType", licenseType);
            draft.put("licenseDetail", licenseDetail);
            draft.put("sourceUrl", sourceUrl);
            draft.put("commonSourceUrl", commonSourceUrl);
            draft.put("workPageUrl", workPageUrl);
            redirectAttributes.addFlashAttribute("metadataErrorImageId", imageId);
            redirectAttributes.addFlashAttribute("metadataError", exception.getMessage());
            redirectAttributes.addFlashAttribute("metadataDraft", draft);
            return imageCardRedirect(destinationId, imageId);
        } catch (IllegalArgumentException exception) {
            throw invalidImageRequest();
        }
        // 저장한 카드로 돌아가 갱신된 출처 요약과 상태를 바로 보이게 한다.
        redirectAttributes.addFlashAttribute("metadataSavedImageId", imageId);
        return imageCardRedirect(destinationId, imageId);
    }

    private String imageCardRedirect(Long destinationId, Long imageId) {
        return managementRedirect(destinationId) + "#image-" + imageId;
    }

    public String updateImageMetadata(Long destinationId, Long imageId, String sourceName,
                                      String photographer, String licenseType,
                                      String licenseDetail, String sourceUrl) {
        try {
            destinationImageService.updateImageMetadata(destinationId, imageId, sourceName,
                    photographer, licenseType, licenseDetail, sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidImageRequest();
        }
        return managementRedirect(destinationId);
    }

    @PostMapping("/{id}/images/sources/bulk")
    public String applyBulkSource(@PathVariable Long id,
                                  @RequestParam(value = "imageIds", required = false) List<Long> imageIds,
                                  @RequestParam(value = "sourceName", required = false) String sourceName,
                                  @RequestParam(value = "photographer", required = false) String photographer,
                                  @RequestParam(value = "licenseType", required = false) String licenseType,
                                  @RequestParam(value = "licenseDetail", required = false) String licenseDetail,
                                  @RequestParam(value = "commonSourceUrl", required = false) String commonSourceUrl,
                                  @RequestParam(value = "overwriteFields", required = false) Set<String> overwriteFields,
                                  @RequestParam(value = "licenseConfirmed", defaultValue = "false") boolean licenseConfirmed,
                                  @RequestParam(value = "overwriteConfirmed", defaultValue = "false") boolean overwriteConfirmed,
                                  RedirectAttributes redirectAttributes) {
        try {
            var result = destinationImageService.applyBulkSource(id, imageIds, sourceName,
                    photographer, licenseType, licenseDetail, commonSourceUrl,
                    overwriteFields, licenseConfirmed, overwriteConfirmed);
            redirectAttributes.addFlashAttribute("bulkSourceResult", result);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("bulkSourceError", exception.getMessage());
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("bulkSourceError", "공통 출처 저장에 실패했습니다. 다시 시도해 주세요.");
        }
        return managementRedirect(id) + "#registered-images";
    }

    @PostMapping("/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long imageId,
                              @RequestParam("destinationId") Long destinationId) {
        try {
            destinationImageService.deleteImage(destinationId, imageId);
        } catch (IllegalArgumentException exception) {
            throw invalidImageRequest();
        }
        return managementRedirect(destinationId);
    }

    private ResponseStatusException invalidImageRequest() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "잘못된 이미지 요청입니다.");
    }

    private String destinationName(Long destinationId) {
        List<DestinationTranslation> translations =
                destinationService.getTranslationsByDestinationId(destinationId);
        if (translations != null) {
            String koreanName = translations.stream()
                    .filter(translation -> "ko".equals(translation.getLanguageCode()))
                    .map(DestinationTranslation::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(null);
            if (koreanName != null) {
                return koreanName;
            }
        }
        return "여행지 #" + destinationId;
    }

    private String managementRedirect(Long destinationId) {
        return "redirect:/admin/destinations/" + destinationId + "/images";
    }
}
