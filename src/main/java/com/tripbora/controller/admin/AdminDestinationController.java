package com.tripbora.controller.admin;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationImageLicenseType;
import com.tripbora.model.DestinationType;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.destination.DestinationNotFoundException;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DuplicateDestinationException;
import com.tripbora.service.destination.DuplicateTourApiDestinationException;
import com.tripbora.service.destination.DuplicateWikidataDestinationException;
import com.tripbora.service.destination.InvalidMainCategoryException;
import com.tripbora.service.file.UnsupportedImageFormatException;
import com.tripbora.service.info.AccommodationInfoService;
import com.tripbora.service.info.ActivityInfoService;
import com.tripbora.service.info.AttractionInfoService;
import com.tripbora.service.info.RestaurantInfoService;
import com.tripbora.service.info.ShopInfoService;
import com.tripbora.service.kto.InvalidKtoSelectedPhotosException;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
import com.tripbora.service.kto.KtoTourImportContentType;
import com.tripbora.service.wikidata.CommonsApiException;
import com.tripbora.service.wikidata.CommonsPhotoDownloadException;
import com.tripbora.service.wikidata.WikidataApiException;
import com.tripbora.service.wikidata.WikipediaApiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

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
    /** 유형별 상세정보의 언어별 입력값을 수정 화면에 복원할 때만 쓴다. */
    private final RestaurantInfoService restaurantInfoService;
    private final AttractionInfoService attractionInfoService;
    private final AccommodationInfoService accommodationInfoService;
    private final ActivityInfoService activityInfoService;
    private final ShopInfoService shopInfoService;

    public AdminDestinationController(DestinationService destinationService,
                                      CategoryService categoryService,
                                      AmenityService amenityService,
                                      CountryCategoryService countryCategoryService,
                                      KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser,
                                      DestinationSaveOrchestrationService destinationSaveOrchestrationService,
                                      RestaurantInfoService restaurantInfoService,
                                      AttractionInfoService attractionInfoService,
                                      AccommodationInfoService accommodationInfoService,
                                      ActivityInfoService activityInfoService,
                                      ShopInfoService shopInfoService) {
        this.destinationService = destinationService;
        this.categoryService = categoryService;
        this.amenityService = amenityService;
        this.countryCategoryService = countryCategoryService;
        this.ktoSelectedPhotoRequestParser = ktoSelectedPhotoRequestParser;
        this.destinationSaveOrchestrationService = destinationSaveOrchestrationService;
        this.restaurantInfoService = restaurantInfoService;
        this.attractionInfoService = attractionInfoService;
        this.accommodationInfoService = accommodationInfoService;
        this.activityInfoService = activityInfoService;
        this.shopInfoService = shopInfoService;
    }

    /** TourAPI 지역별 일괄 가져오기 화면. 후보 조회와 등록은 모두 /admin/api/kto/tour/bulk 가 맡는다. */
    @GetMapping("/kto-import")
    public String showKtoImportPage(Model model) {
        model.addAttribute("importContentTypes", KtoTourImportContentType.supported());
        return "admin/destinations/kto-import";
    }

    /** 해외(Wikidata) 일괄 등록 화면. 검색·검토·등록은 /admin/api/wikidata/bulk 가 맡는다. */
    @GetMapping("/wikidata-import")
    public String showWikidataImportPage(Model model) {
        model.addAttribute("destinationTypes", DESTINATION_TYPE_LABELS);
        model.addAttribute("seasons", SEASON_LABELS);
        // 해외 지역 선택에서 국내 루트를 빼는 기준. 숫자 ID 를 화면에 하드코딩하지 않는다.
        model.addAttribute("domesticRootId", countryCategoryService.getKoreaRootId());
        return "admin/destinations/wikidata-import";
    }

    /** 등록폼의 유형·시즌 선택지와 같은 순서·이름. */
    private static final Map<String, String> DESTINATION_TYPE_LABELS = orderedLabels(
            "ATTRACTION", "관광지", "ACCOMMODATION", "숙소", "RESTAURANTS", "음식점",
            "CAFE", "카페", "SHOP", "쇼핑", "ACTIVITY", "체험/액티비티");
    private static final Map<String, String> SEASON_LABELS = orderedLabels(
            "SPRING", "봄", "SUMMER", "여름", "FALL", "가을", "WINTER", "겨울", "ALL_SEASONS", "사계절");

    private static Map<String, String> orderedLabels(String... pairs) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) labels.put(pairs[index], pairs[index + 1]);
        return java.util.Collections.unmodifiableMap(labels);
    }

    // 여행지 등록
    @GetMapping("/create")
    public String showCreateForm(Model model,
                                 @RequestParam(defaultValue = "ko") String lang) {
        prepareCreateFormModel(model, new DestinationForm(), lang);
        return "admin/destinations/create";
    }

    /** 번역 입력 슬롯의 언어 이름. 탭 title/aria-label 처럼 풀어 쓸 자리에 쓴다. */
    private static final Map<String, String> TRANSLATION_LANGUAGE_LABELS = Map.of(
            "en", "영어",
            "ja", "일본어",
            "zh-CN", "중국어(간체)",
            "zh-TW", "중국어(번체)"
    );

    /** 번역 언어 탭 버튼에 찍는 이름. 관리자 화면이므로 한국어로 짧게 쓴다. */
    private static final Map<String, String> TRANSLATION_TAB_LABELS = Map.of(
            "en", "영어",
            "ja", "일본어",
            "zh-CN", "간체",
            "zh-TW", "번체"
    );

    private void prepareCreateFormModel(Model model, DestinationForm form, String lang) {
        model.addAttribute("destinationForm", form);
        model.addAttribute("wikipediaSourcesPending", form.getWikipediaRevisionIds() != null
                && form.getWikipediaRevisionIds().stream().anyMatch(Objects::nonNull));
        model.addAttribute("imageLicenseOptions", DestinationImageLicenseType.values());
        model.addAttribute("imageLicenseCodes", Arrays.stream(DestinationImageLicenseType.values())
                .map(DestinationImageLicenseType::getCode)
                .toList());
        // 지역 선택 UI 의 국내/해외 구분 기준. 숫자 ID 를 화면에 하드코딩하지 않는다.
        model.addAttribute("domesticRootId", countryCategoryService.getKoreaRootId());
        addCategoryModel(model);
        addAmenityModel(model, lang);
        addRestaurantTranslationModel(model);
    }

    /** 번역 탭 라벨. 언어 코드는 폼 슬롯에 고정돼 있고 화면 이름만 여기서 정한다. */
    private void addRestaurantTranslationModel(Model model) {
        model.addAttribute("translationLanguageLabels", TRANSLATION_LANGUAGE_LABELS);
        model.addAttribute("translationTabLabels", TRANSLATION_TAB_LABELS);
    }

    /** 전체 카테고리(기존 binding)와 여행지 유형별 카테고리 목록. */
    private void addCategoryModel(Model model) {
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("attractionCategories",
                categoryService.getByDestinationTypes(DestinationType.ATTRACTION));
        model.addAttribute("accommodationCategories",
                categoryService.getByDestinationTypes(DestinationType.ACCOMMODATION));
        // 음식점과 카페는 같은 입력 화면을 쓰므로 두 유형을 합쳐 전달한다.
        model.addAttribute("restaurantCategories",
                categoryService.getByDestinationTypes(
                        DestinationType.RESTAURANTS, DestinationType.CAFE));
        model.addAttribute("activityCategories",
                categoryService.getByDestinationTypes(DestinationType.ACTIVITY));
        model.addAttribute("shopCategories",
                categoryService.getByDestinationTypes(DestinationType.SHOP));
        // 화면 필터는 이 태그(카테고리 → 적용 가능한 유형)만 보고 동작한다.
        model.addAttribute("categoryTypeTags", categoryService.getCategoryDestinationTypeTags());
    }

    /** 여행지 유형별 편의시설 목록. 유형 매핑이 없는 편의시설은 전체 목록에서만 보인다. */
    private void addAmenityModel(Model model, String lang) {
        model.addAttribute("attractionAmenities",
                amenityService.getAmenityTranslationsByDestinationTypes(lang, DestinationType.ATTRACTION));
        model.addAttribute("accommodationAmenities",
                amenityService.getAmenityTranslationsByDestinationTypes(lang, DestinationType.ACCOMMODATION));
        // 음식점과 카페는 같은 입력 화면과 저장 테이블을 쓰므로 두 유형을 합쳐 전달한다.
        model.addAttribute("restaurantAmenities",
                amenityService.getAmenityTranslationsByDestinationTypes(
                        lang, DestinationType.RESTAURANTS, DestinationType.CAFE));
        model.addAttribute("activityAmenities",
                amenityService.getAmenityTranslationsByDestinationTypes(lang, DestinationType.ACTIVITY));
        model.addAttribute("shopAmenities",
                amenityService.getAmenityTranslationsByDestinationTypes(lang, DestinationType.SHOP));
        model.addAttribute("allAmenities", amenityService.getAllAmenityTranslations(lang));
        // 화면 필터는 이 태그(편의시설 → 적용 가능한 유형)만 보고 동작한다.
        model.addAttribute("amenityTypeTags", amenityService.getAmenityDestinationTypeTags());
    }

    @PostMapping
    public String registerDestination(
            @Valid @ModelAttribute("destinationForm") DestinationForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model,
            HttpServletResponse response,
            @RequestParam(defaultValue = "ko") String lang) {
        rejectInvalidMainCategory(form, bindingResult);
        if (bindingResult.hasErrors()) {
            // 폼이 맨 위부터 다시 그려지므로, 칸 옆 오류와 별도로 위쪽 안내에도 원인을 모아 보여준다.
            model.addAttribute("registrationError", bindingErrorSummary(bindingResult));
            prepareCreateFormModel(model, form, lang);
            return "admin/destinations/create";
        }

        List<KtoSelectedPhotoRequest> selectedKtoPhotos = parseSelectedKtoPhotos(form);

        try {
            destinationSaveOrchestrationService.registerDestination(
                    form, userDetails.getId(), selectedKtoPhotos);
        } catch (DuplicateWikidataDestinationException exception) {
            return duplicateWikidataForm(form, model, response, lang);
        } catch (DuplicateDestinationException exception) {
            return duplicateDestinationForm(form, exception.getCheck(), model, response, lang);
        } catch (DuplicateTourApiDestinationException exception) {
            return duplicateTourApiForm(form, model, response, lang);
        } catch (DuplicateKeyException exception) {
            if (form.getWikidataQid() != null
                    && destinationService.findWikidataDestinationId(form.getWikidataQid()) != null) {
                return duplicateWikidataForm(form, model, response, lang);
            }
            if (destinationService.findTourApiDestinationId(form.getKtoContentId()) != null) {
                return duplicateTourApiForm(form, model, response, lang);
            }
            throw exception;
        } catch (InvalidKtoSelectedPhotosException exception) {
            throw invalidKtoSelection();
        } catch (UnsupportedImageFormatException | DestinationImageService.InvalidSourceUrlException exception) {
            // 잘못된 이미지는 입력 오류이므로 400 을 유지하되 등록 폼 안에서 이유를 보여준다
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("imageError", exception.getMessage());
            prepareCreateFormModel(model, form, lang);
            return "admin/destinations/create";
        } catch (IllegalArgumentException | WikidataApiException | WikipediaApiException | NoSuchElementException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("registrationError", exception.getMessage());
            prepareCreateFormModel(model, form, lang);
            return "admin/destinations/create";
        } catch (CommonsApiException | CommonsPhotoDownloadException exception) {
            // Commons 조회·내려받기 실패는 외부 제공처 문제라 502로 두고, 사진을 제외해 다시 등록할 수 있게 폼을 유지한다.
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            model.addAttribute("registrationError", exception.getMessage());
            prepareCreateFormModel(model, form, lang);
            return "admin/destinations/create";
        } catch (DataAccessException | IllegalStateException exception) {
            if (form.getWikidataQid() == null || form.getWikidataQid().isBlank()) throw exception;
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            model.addAttribute("registrationError",
                    "Wikidata 여행지·Wikipedia 출처·Commons 사진 저장에 실패했습니다. 입력값은 저장되지 않았습니다. 다시 시도해 주세요.");
            prepareCreateFormModel(model, form, lang);
            return "admin/destinations/create";
        }
        // Wikidata·KTO·수동 등록 모두 성공하면 방금 등록한 여행지를 확인할 수 있는 관리자 목록으로 보낸다.
        return "redirect:/admin/destinations";
    }

    /** 형식 변환 오류는 스프링 기본 문구 대신 관리자용 짧은 안내로 바꾼다. */
    private String bindingErrorSummary(BindingResult bindingResult) {
        String summary = bindingResult.getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? BINDING_FIELD_LABELS.getOrDefault(error.getField(), "입력값") + " 형식을 확인해 주세요."
                        : error.getDefaultMessage())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
        return summary.isBlank() ? "입력값을 확인해 주세요." : summary;
    }

    private static final Map<String, String> BINDING_FIELD_LABELS = Map.of(
            "latitude", "위도",
            "longitude", "경도",
            "regionId", "지역",
            "mainCategoryId", "대표 카테고리"
    );

    /** 선택하지 않은 카테고리를 대표로 보냈으면 저장하지 않고 카테고리 칸에 알린다. 규칙은 저장 서비스와 같다. */
    private void rejectInvalidMainCategory(DestinationForm form, BindingResult bindingResult) {
        if (bindingResult.hasFieldErrors("mainCategoryId")) {
            return;
        }
        try {
            DestinationService.resolveMainCategoryId(form.getCategoryIds(), form.getMainCategoryId());
        } catch (InvalidMainCategoryException exception) {
            bindingResult.rejectValue("mainCategoryId", "invalid", exception.getMessage());
        }
    }

    private String duplicateWikidataForm(DestinationForm form, Model model,
                                         HttpServletResponse response, String lang) {
        response.setStatus(HttpStatus.CONFLICT.value());
        model.addAttribute("registrationError", "이미 등록된 Wikidata 여행지입니다.");
        model.addAttribute("existingWikidataDestinationId",
                destinationService.findWikidataDestinationId(form.getWikidataQid()));
        prepareCreateFormModel(model, form, lang);
        return "admin/destinations/create";
    }

    /**
     * 저장 직전 공통 중복 판별에 걸린 외부 후보. 확정 중복이면 기존 여행지로 안내하고,
     * 중복 가능성이면 근거를 보여주고 '다른 여행지 확인'을 체크해 다시 등록할 수 있게 한다.
     */
    private String duplicateDestinationForm(DestinationForm form, DestinationDuplicateCheck check, Model model,
                                            HttpServletResponse response, String lang) {
        response.setStatus(HttpStatus.CONFLICT.value());
        String existing = "#" + check.destinationId()
                + (check.destinationName() == null ? "" : " " + check.destinationName());
        model.addAttribute("registrationError", check.confirmed()
                ? "이미 등록된 여행지입니다: " + existing + " (" + check.message() + ")."
                : "기존 여행지 " + existing + "와 같은 곳일 수 있습니다 (" + check.message() + ").");
        model.addAttribute("existingDestinationId", check.destinationId());
        if (check.needsReview()) {
            model.addAttribute("possibleDuplicate", check);
        }
        prepareCreateFormModel(model, form, lang);
        return "admin/destinations/create";
    }

    /** 고른 TourAPI 후보(contentId)가 이미 등록돼 있으면 저장하지 않고 기존 여행지로 안내한다. */
    private String duplicateTourApiForm(DestinationForm form, Model model,
                                        HttpServletResponse response, String lang) {
        response.setStatus(HttpStatus.CONFLICT.value());
        model.addAttribute("registrationError", "이미 등록된 TourAPI 여행지입니다.");
        model.addAttribute("existingDestinationId",
                destinationService.findTourApiDestinationId(form.getKtoContentId()));
        prepareCreateFormModel(model, form, lang);
        return "admin/destinations/create";
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
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        // 공백만 입력한 검색어는 검색 조건 없음으로 본다.
        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();

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

        var destinationList = destinationService.getDestinationsByRegionIds(regionIds, searchKeyword);
        model.addAttribute("destinationList", destinationList);
        model.addAttribute("type", type);
        model.addAttribute("keyword", searchKeyword);

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
        model.addAttribute("wikipediaSources", destinationService.getWikipediaSourcesByDestinationId(id));
        // 유형별 상세정보의 언어별 값은 저장된 줄이 있으면 각 슬롯으로 되돌린다.
        if (detailDto != null && detailDto.getRestaurantInfo() != null) {
            form.setRestaurantInfoTranslations(restaurantInfoService.getTranslationForms(id));
        }
        if (detailDto != null && detailDto.getAttractionInfo() != null) {
            form.setAttractionInfoTranslations(attractionInfoService.getTranslationForms(id));
        }
        if (detailDto != null && detailDto.getAccommodationInfo() != null) {
            form.setAccommodationInfoTranslations(accommodationInfoService.getTranslationForms(id));
        }
        if (detailDto != null && detailDto.getActivityInfo() != null) {
            form.setActivityInfoTranslations(activityInfoService.getTranslationForms(id));
        }
        if (detailDto != null && detailDto.getShopInfo() != null) {
            form.setShopInfoTranslations(shopInfoService.getTranslationForms(id));
        }

        prepareEditFormModel(model, form, lang);
        return "admin/destinations/edit";
    }

    private void prepareEditFormModel(Model model, DestinationForm form, String lang) {
        model.addAttribute("destinationForm", form);
        if (form.getDestinationId() != null) {
            model.addAttribute("wikipediaSources",
                    destinationService.getWikipediaSourcesByDestinationId(form.getDestinationId()));
        }
        model.addAttribute("domesticRootId", countryCategoryService.getKoreaRootId());
        model.addAttribute("regionPathIds", joinRegionPathIds(form.getRegionId()));
        addCategoryModel(model);
        addAmenityModel(model, lang);
        addRestaurantTranslationModel(model);
    }

    // 지역 select를 기존 저장 값으로 복원하기 위한 최상위 -> 현재 지역 ID 경로
    private String joinRegionPathIds(Long regionId) {
        if (regionId == null) {
            return "";
        }
        return countryCategoryService.getRegionPath(regionId).stream()
                .map(region -> String.valueOf(region.getId()))
                .collect(Collectors.joining(","));
    }

    @PostMapping("/edit/{id}")
    public String updateDestination(@PathVariable Long id,
                                    @Valid @ModelAttribute("destinationForm") DestinationForm form,
                                    BindingResult bindingResult,
                                    Model model,
                                    RedirectAttributes redirectAttributes,
                                    @RequestParam(defaultValue = "ko") String lang) {
        form.setDestinationId(id);
        rejectInvalidMainCategory(form, bindingResult);
        if (bindingResult.hasErrors()) {
            prepareEditFormModel(model, form, lang);
            return "admin/destinations/edit";
        }

        try {
            destinationService.updateDestination(id, form);
        } catch (DestinationNotFoundException exception) {
            // 다른 화면에서 이미 지워진 여행지의 stale 폼 저장 → 목록에서 이유를 알려준다
            redirectAttributes.addFlashAttribute("error", "이미 삭제된 여행지입니다.");
        }
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
