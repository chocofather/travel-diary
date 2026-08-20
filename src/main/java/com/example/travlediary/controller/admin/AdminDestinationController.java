package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Controller
@RequestMapping("/admin/destinations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDestinationController {

    private final DestinationService destinationService;
    private final CategoryService categoryService;
    private final AmenityService amenityService;
    private final CountryCategoryService countryCategoryService;
    private final KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser;
    private final DestinationSaveOrchestrationService destinationSaveOrchestrationService;

    public AdminDestinationController(DestinationService destinationService,
                                      CategoryService categoryService,
                                      AmenityService amenityService,
                                      CountryCategoryService countryCategoryService,
                                      KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser,
                                      DestinationSaveOrchestrationService destinationSaveOrchestrationService) {
        this.destinationService = destinationService;
        this.categoryService = categoryService;
        this.amenityService = amenityService;
        this.countryCategoryService = countryCategoryService;
        this.ktoSelectedPhotoRequestParser = ktoSelectedPhotoRequestParser;
        this.destinationSaveOrchestrationService = destinationSaveOrchestrationService;
    }

    // 여행지 등록
    @GetMapping("/create")
    public String showCreateForm(Model model,
                                 @RequestParam(defaultValue = "ko") String lang) {
        model.addAttribute("destinationForm", new DestinationForm());
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("attractionAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("accommodationAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("restaurantAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("activityAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("shopAmenities", amenityService.getAllAmenityTranslations(lang));
        return "admin/destinations/create";
    }

    @PostMapping
    public String registerDestination(@ModelAttribute DestinationForm form,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KtoSelectedPhotoRequest> selectedKtoPhotos = parseSelectedKtoPhotos(form);
        System.out.println("regionId: " + form.getRegionId());  // regionId 출력 확인

        try {
            destinationSaveOrchestrationService.registerDestination(
                    form, userDetails.getId(), selectedKtoPhotos);
        } catch (InvalidKtoSelectedPhotosException exception) {
            throw invalidKtoSelection();
        }
        return "redirect:/admin";
    }


    // 여행지 관리 리스트 - 필터 추가
    @GetMapping
    public String showDestinationList(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "continentId", required = false) Long continentId,
            @RequestParam(value = "countryId", required = false) Long countryId,
            @RequestParam(value = "cityId", required = false) Long cityId,
            @RequestParam(value = "regionId", required = false) Long regionId,
            @RequestParam(value = "districtId", required = false) Long districtId,
            Model model) {

        System.out.println("type: " + type);
        System.out.println("continentId: " + continentId);

      /*  // 기본값 설정
        if (type == null) {
            type = "overseas";  // 기본값을 "overseas"로 설정
        }
*/
        List<Long> regionIds;
        Long koreaId = countryCategoryService.getKoreaRootId();
        CountryCategory selectedDistrict = null;

        // 국내 하위 지역은 선택한 시/도의 실제 자식일 때만 사용한다.
        if ("domestic".equals(type) && regionId != null && districtId != null) {
            CountryCategory district = countryCategoryService.getById(districtId);
            if (district != null && Objects.equals(district.getParentId(), regionId)) {
                selectedDistrict = district;
            }
        }

        // 1. 최하위 선택 우선
        if (selectedDistrict != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(selectedDistrict.getId());
        } else if (regionId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
        } else if (cityId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(cityId);
        } else if (countryId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(countryId);
        } else if (continentId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(continentId);
        } else if ("domestic".equals(type)) {
            regionIds = koreaId != null ? countryCategoryService.getAllRegionIdsUnder(koreaId) : Collections.emptyList();
        } else if ("overseas".equals(type)) {
            regionIds = new ArrayList<>();
            for (Long id : countryCategoryService.getOverseasRootIds()) {
                regionIds.addAll(countryCategoryService.getAllRegionIdsUnder(id));
            }
        } else {
            // 전체 리스트 (국내 + 해외)
            regionIds = new ArrayList<>();
            if (koreaId != null) {
                regionIds.addAll(countryCategoryService.getAllRegionIdsUnder(koreaId));
            }
            for (Long id : countryCategoryService.getOverseasRootIds()) {
                regionIds.addAll(countryCategoryService.getAllRegionIdsUnder(id));
            }
        }

        var destinationList = destinationService.getDestinationsByRegionIds(regionIds);
        model.addAttribute("destinationList", destinationList);
        model.addAttribute("type", type);

        // 대륙 리스트 (depth=1)
        var continents = countryCategoryService.getRegionsByDepth(1);
        model.addAttribute("continents", continents);
        model.addAttribute("continentId", continentId);

        // 선택된 대륙의 국가 (depth=2)
        List countries = null;
        if (continentId != null) {
            countries = countryCategoryService.getRegionsByDepthAndParent(2, continentId);
        }
        model.addAttribute("countries", countries);
        model.addAttribute("countryId", countryId);

        // 선택된 국가의 도시 (depth=3)
        List cities = null;
        if (countryId != null) {
            cities = countryCategoryService.getRegionsByDepthAndParent(3, countryId);
        }
        model.addAttribute("cities", cities);
        model.addAttribute("cityId", cityId);

        // 국내: depth 값 대신 parent_id로 시/도와 시/군/구를 조회한다.
        List<CountryCategory> regions = null;
        List<CountryCategory> districts = null;
        if ("domestic".equals(type) && koreaId != null) {
            regions = countryCategoryService.getRegionsByParentId(koreaId);
            if (regionId != null) {
                districts = countryCategoryService.getRegionsByParentId(regionId);
            }
        }
        model.addAttribute("regions", regions);
        model.addAttribute("districts", districts);
        model.addAttribute("regionId", regionId);
        model.addAttribute("districtId", selectedDistrict != null ? selectedDistrict.getId() : null);

        // **선택된 리스트 이름 구하기**
        String selectedRegionName = "전체 리스트";
        if (selectedDistrict != null) {
            selectedRegionName = selectedDistrict.getRegionName() + " 리스트";
        } else if (regionId != null) {
            var region = countryCategoryService.getById(regionId);
            if (region != null) selectedRegionName = region.getRegionName() + " 리스트";
        } else if (cityId != null) {
            var city = countryCategoryService.getById(cityId);
            if (city != null) selectedRegionName = city.getRegionName() + " 리스트";
        } else if (countryId != null) {
            var country = countryCategoryService.getById(countryId);
            if (country != null) selectedRegionName = country.getRegionName() + " 리스트";
        } else if (continentId != null) {
            var conti = countryCategoryService.getById(continentId);
            if (conti != null) selectedRegionName = conti.getRegionName() + " 리스트";
        } else if ("domestic".equals(type)) {
            selectedRegionName = "국내 리스트";
        } else if ("overseas".equals(type)) {
            selectedRegionName = "해외 리스트";
        }
        model.addAttribute("selectedRegionName", selectedRegionName);

        return "admin/destinations/list";
    }


    @PostMapping("/{id}/delete")
    public String deleteDestination(@PathVariable Long id) {
        destinationService.deleteById(id);
        return "redirect:/admin/destinations";
    }

    // 여행지 수정 폼
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model,
                               @RequestParam(defaultValue = "ko") String lang) {
        var detailDto = destinationService.getDestinationDetailWithInfo(id);
        var translations = destinationService.getTranslationsByDestinationId(id);
        var form = DestinationForm.fromDetailDto(detailDto, translations);

        model.addAttribute("destinationForm", form);
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("attractionAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("accommodationAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("restaurantAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("activityAmenities", amenityService.getAllAmenityTranslations(lang));
        model.addAttribute("shopAmenities", amenityService.getAllAmenityTranslations(lang));
        return "admin/destinations/edit";
    }

    @PostMapping("/edit/{id}")
    public String updateDestination(@PathVariable Long id,
                                    @ModelAttribute DestinationForm form) {
        destinationService.updateDestination(id, form);
        return "redirect:/admin/destinations";
    }

    private List<KtoSelectedPhotoRequest> parseSelectedKtoPhotos(DestinationForm form) {
        try {
            return ktoSelectedPhotoRequestParser.parse(form.getKtoSelectedPhotosJson());
        } catch (InvalidKtoSelectedPhotosException exception) {
            throw invalidKtoSelection();
        }
    }

    private ResponseStatusException invalidKtoSelection() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "선택한 관광사진 정보가 올바르지 않습니다."
        );
    }
}
