package com.example.travlediary.controller.admin;

import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 등록/수정 폼의 유형별 편의시설 목록 계약.
 * 각 model attribute 는 해당 여행지 유형에 매핑된 편의시설만 담는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDestinationAmenityModelTest {

    @Mock
    private DestinationService destinationService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private AmenityService amenityService;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private DestinationSaveOrchestrationService destinationSaveOrchestrationService;
    @Mock
    private RestaurantInfoService restaurantInfoService;
    @Mock
    private AttractionInfoService attractionInfoService;
    @Mock
    private AccommodationInfoService accommodationInfoService;

    private AdminDestinationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminDestinationController(
                destinationService,
                categoryService,
                amenityService,
                countryCategoryService,
                new KtoSelectedPhotoRequestParser(
                        new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()),
                destinationSaveOrchestrationService,
                restaurantInfoService,
                attractionInfoService,
                accommodationInfoService);

        when(amenityService.getAmenityTranslationsByDestinationTypes("ko", DestinationType.ATTRACTION))
                .thenReturn(List.of(translation(1, "관광지 편의시설")));
        when(amenityService.getAmenityTranslationsByDestinationTypes("ko", DestinationType.ACCOMMODATION))
                .thenReturn(List.of(translation(2, "숙소 편의시설")));
        when(amenityService.getAmenityTranslationsByDestinationTypes(
                "ko", DestinationType.RESTAURANTS, DestinationType.CAFE))
                .thenReturn(List.of(translation(3, "음식점·카페 편의시설")));
        when(amenityService.getAmenityTranslationsByDestinationTypes("ko", DestinationType.ACTIVITY))
                .thenReturn(List.of(translation(4, "액티비티 편의시설")));
        when(amenityService.getAmenityTranslationsByDestinationTypes("ko", DestinationType.SHOP))
                .thenReturn(List.of(translation(5, "쇼핑 편의시설")));
        when(amenityService.getAllAmenityTranslations("ko"))
                .thenReturn(List.of(translation(1, "관광지 편의시설"), translation(9, "미분류 편의시설")));
        when(amenityService.getAmenityDestinationTypeTags())
                .thenReturn(java.util.Map.of(1, "ATTRACTION"));
    }

    @Test
    void createFormReceivesOneListPerDestinationType() {
        Model model = new ExtendedModelMap();

        controller.showCreateForm(model, "ko");

        assertTypeLists(model);
    }

    @Test
    void editFormReceivesTheSameListsAsCreate() {
        Model model = new ExtendedModelMap();

        controller.showEditForm(9L, model, "ko");

        assertTypeLists(model);
    }

    private void assertTypeLists(Model model) {
        assertThat(names(model, "attractionAmenities")).containsExactly("관광지 편의시설");
        assertThat(names(model, "accommodationAmenities")).containsExactly("숙소 편의시설");
        // RESTAURANTS 와 CAFE 는 화면/저장 구조를 공유하므로 한 목록으로 합쳐 전달한다
        assertThat(names(model, "restaurantAmenities")).containsExactly("음식점·카페 편의시설");
        assertThat(names(model, "activityAmenities")).containsExactly("액티비티 편의시설");
        assertThat(names(model, "shopAmenities")).containsExactly("쇼핑 편의시설");
        // [전체] 필터용 전체 목록과, 필터가 쓰는 유형 태그를 함께 내려준다
        assertThat(names(model, "allAmenities"))
                .containsExactly("관광지 편의시설", "미분류 편의시설");
        assertThat(model.getAttribute("amenityTypeTags"))
                .isEqualTo(java.util.Map.of(1, "ATTRACTION"));
    }

    @SuppressWarnings("unchecked")
    private List<String> names(Model model, String attribute) {
        Object value = model.getAttribute(attribute);
        assertThat(value).as("model attribute %s", attribute).isInstanceOf(List.class);
        return ((List<AmenityTranslation>) value).stream().map(AmenityTranslation::getName).toList();
    }

    private AmenityTranslation translation(int amenityId, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setLanguageCode("ko");
        translation.setName(name);
        return translation;
    }
}
