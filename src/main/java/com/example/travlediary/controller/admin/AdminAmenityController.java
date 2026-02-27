package com.example.travlediary.controller.admin;

import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.service.amenity.AmenityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Controller
@RequestMapping("/admin/amenities")
@RequiredArgsConstructor
public class AdminAmenityController {
    private final AmenityService amenityService;

    // 1. Amenity 등록 폼
    @GetMapping("/create")
    public String showCreateForm() {
        return "admin/amenities/create"; // amenity 등록 폼
    }

    // 2. Amenity 등록 처리
    @PostMapping("/create")
    public String createAmenity(@RequestParam("code") String code, Model model) {
        if (code == null || code.trim().isEmpty()) {
            model.addAttribute("error", "코드를 입력하세요.");
            return "admin/amenities/create";
        }
        amenityService.registerAmenity(code);
        return "redirect:/admin/amenities/list";
    }

    // 3. Amenity 전체 리스트 (번역등록 버튼 포함)
    @GetMapping("/list")
    public String listAmenities(Model model) {
        List<Amenity> amenityList = amenityService.getAllAmenities();
        model.addAttribute("amenities", amenityList);
        return "admin/amenities/list"; // amenity 리스트(테이블)
    }

    // 4. Amenity 번역 등록 폼
    @GetMapping("/{amenityId}/translations/create")
    public String showTranslationForm(@PathVariable Integer amenityId, Model model) {
        model.addAttribute("amenityId", amenityId);
        return "admin/amenities/translation-create"; // 번역 등록 폼
    }

    // 5. Amenity 번역 등록 처리
    @PostMapping("/{amenityId}/translations/create")
    public String createTranslation(
            @PathVariable Integer amenityId,
            @RequestParam("languageCode") String languageCode,
            @RequestParam("name") String name,
            Model model
    ) {
        // 단순 필수값 체크
        if (languageCode == null || languageCode.trim().isEmpty() ||
                name == null || name.trim().isEmpty()) {
            model.addAttribute("amenityId", amenityId);
            model.addAttribute("error", "언어코드와 이름을 입력하세요.");
            return "admin/amenities/translation-create";
        }
        // 중복 번역 체크 (중복 있으면 등록X)
        AmenityTranslation exist = amenityService.findTranslation(amenityId, languageCode);
        if (exist != null) {
            model.addAttribute("amenityId", amenityId);
            model.addAttribute("error", "이미 해당 언어 번역이 있습니다.");
            return "admin/amenities/translation-create";
        }
        amenityService.registerAmenityTranslation(amenityId, languageCode, name);
        return "redirect:/admin/amenities/" + amenityId + "/translations";
    }

    // 6. Amenity별 번역 리스트 보기 (수정/삭제 없이)
    @GetMapping("/{amenityId}/translations")
    public String listTranslations(@PathVariable Integer amenityId, Model model) {
        List<AmenityTranslation> translations = amenityService.getTranslationsByAmenityId(amenityId);
        model.addAttribute("translations", translations);
        model.addAttribute("amenityId", amenityId);
        return "admin/amenities/translation-list"; // amenity별 번역 리스트
    }
}
