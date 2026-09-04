package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.ShopInfoTranslationForm;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.ShopInfo;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
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
 * 쇼핑 수정 화면의 번역 복원.
 *
 * <p>저장된 언어 값이 각 탭 슬롯으로 돌아와야 관리자가 이어서 고칠 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class AdminShopTranslationEditFormTest {

    private static final Long DESTINATION_ID = 51L;

    @Mock private DestinationService destinationService;
    @Mock private CategoryService categoryService;
    @Mock private AmenityService amenityService;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private DestinationSaveOrchestrationService destinationSaveOrchestrationService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private ActivityInfoService activityInfoService;
    @Mock private ShopInfoService shopInfoService;

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
                activityInfoService,
                shopInfoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }

    @Test
    void theEditFormRestoresTheStoredShopTranslationsIntoTheirSlots() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(DESTINATION_ID))
                .thenReturn(detailDto());
        when(destinationService.getTranslationsByDestinationId(DESTINATION_ID))
                .thenReturn(List.of());
        when(shopInfoService.getTranslationForms(DESTINATION_ID)).thenReturn(storedSlots());

        var result = mockMvc.perform(get("/admin/destinations/edit/" + DESTINATION_ID))
                .andExpect(status().isOk())
                .andReturn();

        DestinationForm form = (DestinationForm) result.getModelAndView()
                .getModel().get("destinationForm");
        assertThat(form.getShopInfoTranslations())
                .extracting(ShopInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(form.getShopInfoTranslations().get(0).getMainProducts())
                .isEqualTo("Clothing, accessories, food");
        assertThat(form.getShopInfoTranslations().get(1).getGuide()).isEqualTo("団体は要予約");
        // 한국어 원본 입력은 예전처럼 shopInfo 로 돌아온다
        assertThat(form.getShopInfo().getMainProducts()).isEqualTo("의류, 소품, 식품");
        // 다른 유형의 번역은 읽지 않는다
        verify(activityInfoService, never()).getTranslationForms(DESTINATION_ID);
        verify(restaurantInfoService, never()).getTranslationForms(DESTINATION_ID);
    }

    private DestinationDetailDto detailDto() {
        Destination destination = new Destination();
        destination.setId(DESTINATION_ID);
        destination.setType(DestinationType.SHOP);

        ShopInfo info = new ShopInfo();
        info.setDestinationId(DESTINATION_ID);
        info.setMainProducts("의류, 소품, 식품");

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        dto.setShopInfo(info);
        return dto;
    }

    private List<ShopInfoTranslationForm> storedSlots() {
        List<ShopInfoTranslationForm> slots = DestinationForm.newShopInfoTranslationSlots();
        slots.get(0).setMainProducts("Clothing, accessories, food");
        slots.get(1).setGuide("団体は要予約");
        return slots;
    }
}
