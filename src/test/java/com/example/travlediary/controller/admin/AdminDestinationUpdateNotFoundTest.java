package com.example.travlediary.controller.admin;

import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationNotFoundException;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ShopInfoService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * stale edit 시나리오의 HTTP 계약:
 * 수정 화면을 열어 둔 사이 여행지가 삭제되면 저장 요청은 404 로 끝나야 한다(500 금지).
 */
@ExtendWith(MockitoExtension.class)
class AdminDestinationUpdateNotFoundTest {

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
        AdminDestinationController controller = new AdminDestinationController(
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
                activityInfoService,
                shopInfoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }

    @Test
    void savingAStaleEditFormForADeletedDestinationReturnsToTheListWithAnExplanation() throws Exception {
        doThrow(new DestinationNotFoundException())
                .when(destinationService).updateDestination(eq(9L), any());

        // Whitelabel 404 대신 관리자 목록으로 돌려보내고 이유를 알려준다
        mockMvc.perform(post("/admin/destinations/edit/9")
                        .param("regionId", "31")
                        .param("season", "SPRING")
                        .param("type", "ATTRACTION"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations"))
                .andExpect(flash().attribute("error", "이미 삭제된 여행지입니다."));
    }

    @Test
    void existingDestinationKeepsTheUsualRedirect() throws Exception {
        mockMvc.perform(post("/admin/destinations/edit/9")
                        .param("regionId", "31")
                        .param("season", "SPRING")
                        .param("type", "ATTRACTION"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations"));

        verify(destinationService).updateDestination(eq(9L), any());
    }

    @Test
    void unexpectedServerFailureIsNotHiddenAsNotFound() {
        doThrow(new IllegalStateException("db failure"))
                .when(destinationService).updateDestination(eq(9L), any());

        assertThatThrownBy(() -> mockMvc.perform(post("/admin/destinations/edit/9")
                .param("regionId", "31")
                .param("season", "SPRING")
                .param("type", "ATTRACTION")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
