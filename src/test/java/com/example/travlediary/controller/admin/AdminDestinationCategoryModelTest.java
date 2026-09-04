package com.example.travlediary.controller.admin;

import com.example.travlediary.model.Category;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
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
 * 등록/수정 폼의 유형별 카테고리 목록 계약.
 * 전체 목록(categories)은 그대로 두고, 유형별 목록을 함께 내려준다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDestinationCategoryModelTest {

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
    @Mock
    private ActivityInfoService activityInfoService;

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
                accommodationInfoService,
                activityInfoService);

        when(categoryService.getAll())
                .thenReturn(List.of(category(1L, "역사유적"), category(9L, "미분류 카테고리")));
        when(categoryService.getByDestinationTypes(DestinationType.ATTRACTION))
                .thenReturn(List.of(category(1L, "역사유적")));
        when(categoryService.getByDestinationTypes(DestinationType.ACCOMMODATION))
                .thenReturn(List.of(category(2L, "호텔")));
        when(categoryService.getByDestinationTypes(
                DestinationType.RESTAURANTS, DestinationType.CAFE))
                .thenReturn(List.of(category(3L, "맛집"), category(4L, "디저트")));
        when(categoryService.getByDestinationTypes(DestinationType.ACTIVITY))
                .thenReturn(List.of(category(5L, "체험")));
        when(categoryService.getByDestinationTypes(DestinationType.SHOP))
                .thenReturn(List.of(category(6L, "전통시장")));
        when(categoryService.getCategoryDestinationTypeTags())
                .thenReturn(java.util.Map.of(1L, "ATTRACTION"));
    }

    @Test
    void createFormReceivesOneCategoryListPerDestinationType() {
        Model model = new ExtendedModelMap();

        controller.showCreateForm(model, "ko");

        assertCategoryLists(model);
    }

    @Test
    void editFormReceivesTheSameCategoryListsAsCreate() {
        Model model = new ExtendedModelMap();

        controller.showEditForm(9L, model, "ko");

        assertCategoryLists(model);
    }

    private void assertCategoryLists(Model model) {
        // 기존 전체 목록 binding 은 그대로 (템플릿의 ${categories})
        assertThat(names(model, "categories")).containsExactly("역사유적", "미분류 카테고리");
        assertThat(names(model, "attractionCategories")).containsExactly("역사유적");
        assertThat(names(model, "accommodationCategories")).containsExactly("호텔");
        // 음식점과 카페는 한 화면을 공유하므로 두 유형을 합쳐 전달한다
        assertThat(names(model, "restaurantCategories")).containsExactly("맛집", "디저트");
        assertThat(names(model, "activityCategories")).containsExactly("체험");
        assertThat(names(model, "shopCategories")).containsExactly("전통시장");
        // 화면 필터가 쓰는 유형 태그도 함께 내려준다
        assertThat(model.getAttribute("categoryTypeTags"))
                .isEqualTo(java.util.Map.of(1L, "ATTRACTION"));
    }

    @SuppressWarnings("unchecked")
    private List<String> names(Model model, String attribute) {
        Object value = model.getAttribute(attribute);
        assertThat(value).as("model attribute %s", attribute).isInstanceOf(List.class);
        return ((List<Category>) value).stream().map(Category::getName).toList();
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
