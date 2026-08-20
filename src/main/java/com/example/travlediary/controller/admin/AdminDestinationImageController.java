package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationKtoImageManagementService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/destinations")
public class AdminDestinationImageController {

    private final DestinationImageService destinationImageService;
    private final DestinationService destinationService;
    private final KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser;
    private final DestinationKtoImageManagementService ktoImageManagementService;

    @GetMapping("/{id}/images")
    public String showImageUploadForm(@PathVariable Long id, Model model) {
        List<DestinationImage> images = destinationImageService.getImages(id);
        model.addAttribute("destinationId", id);
        model.addAttribute("destinationName", destinationName(id));
        model.addAttribute("imageList", images);
        model.addAttribute("imageCount", images.size());
        return "admin/destinations/image-upload";
    }

    @PostMapping("/{id}/images")
    public String uploadImages(@PathVariable Long id,
                               @RequestParam("files") MultipartFile[] files) {
        destinationImageService.saveImages(id, files, null, new Integer[0]);
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

    @PostMapping("/images/{imageId}/main")
    public String setMainImage(@RequestParam("destinationId") Long destinationId,
                               @PathVariable Long imageId) {
        destinationImageService.setMainImage(destinationId, imageId);
        return managementRedirect(destinationId);
    }

    @PostMapping("/images/{imageId}/slide")
    public String toggleSlideImage(@RequestParam("destinationId") Long destinationId,
                                   @PathVariable Long imageId) {
        destinationImageService.toggleSlideImage(destinationId, imageId);
        return managementRedirect(destinationId);
    }

    @PostMapping("/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long imageId,
                              @RequestParam("destinationId") Long destinationId) {
        destinationImageService.deleteImageById(imageId);
        return managementRedirect(destinationId);
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
