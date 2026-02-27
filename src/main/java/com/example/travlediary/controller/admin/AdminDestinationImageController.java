package com.example.travlediary.controller.admin;

import com.example.travlediary.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/destinations")
public class AdminDestinationImageController {

    private final DestinationImageService destinationImageService;

    private final String uploadDir = "/Users/minjun/travlediary-uploads/";

    // 이미지 업로드 폼 페이지
    @GetMapping("/{id}/images")
    public String showImageUploadForm(@PathVariable Long id, Model model) {
        model.addAttribute("destinationId", id);
        model.addAttribute("imageList", destinationImageService.getImages(id));
        return "admin/destinations/image-upload";
    }

    // 이미지 업로드 처리
    @PostMapping("/{id}/images")
    public String uploadImages(@PathVariable Long id,
                               @RequestParam("files") MultipartFile[] files,
                               @RequestParam(value = "mainIdx", required = false) Integer mainIdx,
                               @RequestParam(value = "slideIdx", required = false) String slideIdxRaw) {

        Integer[] slideIdx = parseSlideIndexes(slideIdxRaw);
        destinationImageService.saveImages(id, files, mainIdx, slideIdx);
        return "redirect:/admin/destinations";
    }

    private Integer[] parseSlideIndexes(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Integer[]{};
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toArray(Integer[]::new);
    }

    @PostMapping("/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long imageId,
                              @RequestParam("destinationId") Long destinationId) {
        destinationImageService.deleteImageById(imageId);
        return "redirect:/admin/destinations/" + destinationId + "/images";
    }



}
