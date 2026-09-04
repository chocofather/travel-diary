package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
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
                activityInfoService
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
