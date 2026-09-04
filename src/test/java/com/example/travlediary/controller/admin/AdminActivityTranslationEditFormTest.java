package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.ActivityInfoTranslationForm;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 체험/액티비티 수정 화면의 번역 복원.
 *
 * <p>저장된 언어 값이 각 탭 슬롯으로 돌아와야 관리자가 이어서 고칠 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class AdminActivityTranslationEditFormTest {

    private static final Long DESTINATION_ID = 41L;

    @Mock private DestinationService destinationService;
    @Mock private CategoryService categoryService;
    @Mock private AmenityService amenityService;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private DestinationSaveOrchestrationService destinationSaveOrchestrationService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private ActivityInfoService activityInfoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminDestinationController controller = new AdminDestinationController(
                destinationService,
                categoryService,
                amenityService,
                countryCategoryService,
                new KtoSelectedPhotoRequestParser(new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()),
                destinationSaveOrchestrationService,
                restaurantInfoService,
                attractionInfoService,
                accommodationInfoService,
                activityInfoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }

    @Test
    void theEditFormRestoresTheStoredActivityTranslationsIntoTheirSlots() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(DESTINATION_ID))
                .thenReturn(detailDto());
        when(destinationService.getTranslationsByDestinationId(DESTINATION_ID))
                .thenReturn(List.of());
        when(activityInfoService.getTranslationForms(DESTINATION_ID)).thenReturn(storedSlots());

        var result = mockMvc.perform(get("/admin/destinations/edit/" + DESTINATION_ID))
                .andExpect(status().isOk())
                .andReturn();

        DestinationForm form = (DestinationForm) result.getModelAndView()
                .getModel().get("destinationForm");
        assertThat(form.getActivityInfoTranslations())
                .extracting(ActivityInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(form.getActivityInfoTranslations().get(0).getRequiredTime())
                .isEqualTo("About 2 hours");
        assertThat(form.getActivityInfoTranslations().get(1).getGuide()).isEqualTo("雨天中止");
        // 한국어 원본 입력은 예전처럼 activityInfo 로 돌아온다
        assertThat(form.getActivityInfo().getRequiredTime()).isEqualTo("약 2시간");
        // 다른 유형의 번역은 읽지 않는다
        verify(restaurantInfoService, never()).getTranslationForms(DESTINATION_ID);
        verify(accommodationInfoService, never()).getTranslationForms(DESTINATION_ID);
    }

    private DestinationDetailDto detailDto() {
        Destination destination = new Destination();
        destination.setId(DESTINATION_ID);
        destination.setType(DestinationType.ACTIVITY);

        ActivityInfo info = new ActivityInfo();
        info.setDestinationId(DESTINATION_ID);
        info.setRequiredTime("약 2시간");

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        dto.setActivityInfo(info);
        return dto;
    }

    private List<ActivityInfoTranslationForm> storedSlots() {
        List<ActivityInfoTranslationForm> slots =
                DestinationForm.newActivityInfoTranslationSlots();
        slots.get(0).setRequiredTime("About 2 hours");
        slots.get(1).setGuide("雨天中止");
        return slots;
    }
}
