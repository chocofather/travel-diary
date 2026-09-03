package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.amenity.AmenityValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/admin/amenities")
@RequiredArgsConstructor
public class AdminAmenityController {
    private final AmenityService amenityService;

    private static final String CREATE_VIEW = "admin/amenities/create";
    private static final String EDIT_VIEW = "admin/amenities/edit";
    private static final String REDIRECT_LIST = "redirect:/admin/amenities/list";

    private static final Set<String> FORM_FIELDS = Set.of(
            "code", "nameKo", "nameEn", "nameJa", "nameZhCn", "nameZhTw",
            "destinationTypes", "icon");

    /** 체크박스 표시용 라벨. 값 자체는 DestinationType enum 에서 온다. */
    private static final Map<DestinationType, String> DESTINATION_TYPE_LABELS = Map.of(
            DestinationType.ATTRACTION, "여행지",
            DestinationType.RESTAURANTS, "식당",
            DestinationType.CAFE, "카페",
            DestinationType.ACCOMMODATION, "숙소",
            DestinationType.ACTIVITY, "액티비티",
            DestinationType.SHOP, "쇼핑"
    );

    /** 목록의 badge 는 enum 이름 문자열로 조회하므로 같은 라벨을 이름 키로도 제공한다. */
    private static final Map<String, String> DESTINATION_TYPE_LABELS_BY_NAME =
            DESTINATION_TYPE_LABELS.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().name(), Map.Entry::getValue));

    // 1. Amenity 등록 폼
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        // 검증 실패로 다시 그릴 때는 이미 담긴 폼(입력값 + 오류)을 그대로 쓴다.
        if (!model.containsAttribute("amenityForm")) {
            model.addAttribute("amenityForm", new AmenityForm());
        }
        addFormOptions(model);
        return CREATE_VIEW;
    }

    // 2. Amenity 등록 처리 (코드/아이콘/번역/적용 대상 통합)
    @PostMapping("/create")
    public String createAmenity(@ModelAttribute("amenityForm") AmenityForm form,
                                BindingResult bindingResult,
                                Model model) {
        try {
            amenityService.registerAmenity(form);
        } catch (AmenityValidationException exception) {
            rejectValidation(bindingResult, exception.getField(), exception.getMessage());
            addFormOptions(model);
            return CREATE_VIEW;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // 아이콘 검증/저장 실패. 내부 경로나 stack trace 는 노출하지 않는다.
            rejectValidation(bindingResult, "icon", exception.getMessage());
            addFormOptions(model);
            return CREATE_VIEW;
        }
        return REDIRECT_LIST;
    }

    // 2-1. Amenity 수정 폼 (code 는 readonly, 아이콘은 선택 교체)
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("amenityForm")) {
            model.addAttribute("amenityForm", amenityService.getAmenityForm(id));
        }
        prepareEditModel(model, id);
        return EDIT_VIEW;
    }

    // 2-2. Amenity 수정 처리
    @PostMapping("/{id}/edit")
    public String updateAmenity(@PathVariable Integer id,
                                @ModelAttribute("amenityForm") AmenityForm form,
                                BindingResult bindingResult,
                                Model model) {
        form.setId(id);
        try {
            amenityService.updateAmenity(form);
        } catch (AmenityValidationException exception) {
            rejectValidation(bindingResult, exception.getField(), exception.getMessage());
            prepareEditModel(model, id);
            return EDIT_VIEW;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            rejectValidation(bindingResult, "icon", exception.getMessage());
            prepareEditModel(model, id);
            return EDIT_VIEW;
        }
        return REDIRECT_LIST;
    }

    /** 검증 실패로 다시 그릴 때도 현재 아이콘은 저장된 값에서 다시 읽는다. */
    private void prepareEditModel(Model model, Integer id) {
        addFormOptions(model);
        model.addAttribute("amenityId", id);
        model.addAttribute("currentIconUrl", amenityService.getAmenityIconUrl(id));
    }

    private void addFormOptions(Model model) {
        model.addAttribute("destinationTypes", DestinationType.values());
        model.addAttribute("destinationTypeLabels", DESTINATION_TYPE_LABELS);
    }

    private void rejectValidation(BindingResult bindingResult, String field, String message) {
        // 폼에 없는 이름으로 rejectValue 하면 바인딩이 깨지므로 아는 필드만 필드 오류로 남긴다.
        if (field == null || !FORM_FIELDS.contains(field)) {
            bindingResult.reject("amenity.invalid", message);
            return;
        }
        bindingResult.rejectValue(field, "amenity.invalid", message);
    }

    // 3. Amenity 전체 리스트 (번역등록 버튼 포함)
    @GetMapping("/list")
    public String listAmenities(Model model) {
        model.addAttribute("amenities", amenityService.getAdminAmenityRows());
        // 적용 대상 badge 는 기존 태그 맵(편의시설 -> "ATTRACTION CAFE")을 그대로 쓴다.
        model.addAttribute("amenityTypeTags", amenityService.getAmenityDestinationTypeTags());
        model.addAttribute("destinationTypeLabelsByName", DESTINATION_TYPE_LABELS_BY_NAME);
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
