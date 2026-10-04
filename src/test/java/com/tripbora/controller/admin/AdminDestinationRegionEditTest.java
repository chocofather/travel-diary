package com.tripbora.controller.admin;

import com.tripbora.dto.DestinationDetailDto;
import com.tripbora.dto.DestinationForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationType;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.info.AccommodationInfoService;
import com.tripbora.service.info.ActivityInfoService;
import com.tripbora.service.info.AttractionInfoService;
import com.tripbora.service.info.RestaurantInfoService;
import com.tripbora.service.info.ShopInfoService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class AdminDestinationRegionEditTest {

    private static final Long DESTINATION_ID = 5L;
    private static final Long KOREA_ID = 7L;
    private static final Long SEOUL_ID = 38L;
    private static final Long JONGNO_ID = 235L;
    private static final Long HAEUNDAE_ID = 412L;

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
    @Mock
    private ShopInfoService shopInfoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KtoSelectedPhotoRequestParser parser = new KtoSelectedPhotoRequestParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        AdminDestinationController controller = new AdminDestinationController(
                destinationService,
                categoryService,
                amenityService,
                countryCategoryService,
                parser,
                destinationSaveOrchestrationService,
                restaurantInfoService,
                attractionInfoService,
                accommodationInfoService,
                activityInfoService,
                shopInfoService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }

    @Test
    void editFormExposesExistingRegionPathForSelectRestore() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(DESTINATION_ID))
                .thenReturn(detailDto(JONGNO_ID));
        when(destinationService.getTranslationsByDestinationId(DESTINATION_ID))
                .thenReturn(List.of());
        when(countryCategoryService.getRegionPath(JONGNO_ID)).thenReturn(List.of(
                region(KOREA_ID, "대한민국"),
                region(SEOUL_ID, "서울"),
                region(JONGNO_ID, "종로구")
        ));

        var result = mockMvc.perform(get("/admin/destinations/edit/" + DESTINATION_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/destinations/edit"))
                .andExpect(model().attribute("regionPathIds",
                        KOREA_ID + "," + SEOUL_ID + "," + JONGNO_ID))
                .andReturn();

        DestinationForm form = (DestinationForm) result.getModelAndView()
                .getModel().get("destinationForm");
        assertThat(form.getRegionId()).isEqualTo(JONGNO_ID);
    }

    @Test
    void updateKeepsExistingRegionIdWhenRegionSelectionWasNotChanged() throws Exception {
        mockMvc.perform(post("/admin/destinations/edit/" + DESTINATION_ID)
                        .param("regionId", String.valueOf(JONGNO_ID))
                        .param("type", "ATTRACTION")
                        .param("season", "SPRING")
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].description", "설명만 수정"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations"));

        assertThat(capturedForm().getRegionId()).isEqualTo(JONGNO_ID);
    }

    @Test
    void updateAppliesNewlySelectedRegionId() throws Exception {
        mockMvc.perform(post("/admin/destinations/edit/" + DESTINATION_ID)
                        .param("regionId", String.valueOf(HAEUNDAE_ID))
                        .param("type", "ATTRACTION")
                        .param("season", "SPRING"))
                .andExpect(status().is3xxRedirection());

        assertThat(capturedForm().getRegionId()).isEqualTo(HAEUNDAE_ID);
    }

    @Test
    void updateRejectsMissingRegionIdBeforeReachingTheService() throws Exception {
        var result = mockMvc.perform(post("/admin/destinations/edit/" + DESTINATION_ID)
                        .param("regionId", "")
                        .param("type", "ATTRACTION")
                        .param("season", "SPRING"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/destinations/edit"))
                .andReturn();

        verify(destinationService, never()).updateDestination(any(), any());
        BindingResult bindingResult = (BindingResult) result.getModelAndView().getModel()
                .get(BindingResult.MODEL_KEY_PREFIX + "destinationForm");
        assertThat(bindingResult.getFieldError("regionId")).isNotNull();
        assertThat(bindingResult.getFieldError("regionId").getDefaultMessage())
                .isEqualTo("지역을 선택해 주세요.");
    }

    @Test
    void updatePassesTheChosenMainCategoryWithTheSelectedCategories() throws Exception {
        mockMvc.perform(post("/admin/destinations/edit/" + DESTINATION_ID)
                        .param("regionId", String.valueOf(JONGNO_ID))
                        .param("type", "ATTRACTION")
                        .param("season", "SPRING")
                        .param("categoryIds", "10", "20", "30")
                        .param("mainCategoryId", "20"))
                .andExpect(status().is3xxRedirection());

        assertThat(capturedForm().getCategoryIds()).containsExactly(10L, 20L, 30L);
        assertThat(capturedForm().getMainCategoryId()).isEqualTo(20L);
    }

    @Test
    void updateRejectsAMainCategoryThatWasNotSelected() throws Exception {
        var result = mockMvc.perform(post("/admin/destinations/edit/" + DESTINATION_ID)
                        .param("regionId", String.valueOf(JONGNO_ID))
                        .param("type", "ATTRACTION")
                        .param("season", "SPRING")
                        .param("categoryIds", "10", "20")
                        .param("mainCategoryId", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/destinations/edit"))
                .andReturn();

        verify(destinationService, never()).updateDestination(any(), any());
        BindingResult bindingResult = (BindingResult) result.getModelAndView().getModel()
                .get(BindingResult.MODEL_KEY_PREFIX + "destinationForm");
        assertThat(bindingResult.getFieldError("mainCategoryId").getDefaultMessage())
                .isEqualTo("대표 카테고리는 선택한 카테고리 중에서 지정해 주세요.");
    }

    private DestinationForm capturedForm() {
        ArgumentCaptor<DestinationForm> captor = ArgumentCaptor.forClass(DestinationForm.class);
        verify(destinationService).updateDestination(eq(DESTINATION_ID), captor.capture());
        return captor.getValue();
    }

    private DestinationDetailDto detailDto(Long regionId) {
        Destination destination = new Destination();
        destination.setId(DESTINATION_ID);
        destination.setRegionId(regionId);
        destination.setType(DestinationType.ATTRACTION);

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        return dto;
    }

    private CountryCategory region(Long id, String regionName) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setRegionName(regionName);
        return category;
    }
}
