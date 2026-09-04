package com.example.travlediary.controller.admin;

import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
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
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신규 여행지 등록에서 JPEG/PNG 가 아닌 파일을 올렸을 때의 UX 계약.
 * Whitelabel 400 대신 등록 폼을 다시 보여주고 폼 안에서 이유를 알려준다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDestinationInvalidImageUxTest {

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
                accommodationInfoService);
        CustomUserDetails admin = mock(CustomUserDetails.class);
        lenient().when(admin.getId()).thenReturn(7L);
        when(categoryService.getAll()).thenReturn(List.of());
        when(amenityService.getAllAmenityTranslations("ko"))
                .thenReturn(List.<AmenityTranslation>of());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .setCustomArgumentResolvers(principalResolver(admin))
                .build();
    }

    @Test
    void invalidDirectUploadRedrawsTheCreateFormWithTheReason() throws Exception {
        doThrow(new UnsupportedImageFormatException(
                "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다."))
                .when(destinationSaveOrchestrationService)
                .registerDestination(any(), any(), any());

        var result = mockMvc.perform(multipart("/admin/destinations")
                        .file(new MockMultipartFile("images", "fake.jpg", "image/jpeg",
                                "이건 이미지가 아닙니다.".getBytes(StandardCharsets.UTF_8)))
                        .param("regionId", "31")
                        .param("season", "SPRING")
                        .param("type", "ATTRACTION")
                        .param("ktoSelectedPhotosJson", "[]"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException()).isNull();
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin/destinations/create");
        assertThat(result.getModelAndView().getModel().get("imageError"))
                .isEqualTo("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
        // 등록 폼 재렌더링에 필요한 모델과 입력값이 복구된다
        assertThat(result.getModelAndView().getModel()).containsKeys(
                "destinationForm", "categories", "attractionAmenities", "shopAmenities");
    }

    @Test
    void validUploadKeepsTheExistingRegistrationRedirect() throws Exception {
        mockMvc.perform(multipart("/admin/destinations")
                        .param("regionId", "31")
                        .param("season", "SPRING")
                        .param("type", "ATTRACTION")
                        .param("ktoSelectedPhotosJson", "[]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void unexpectedServerFailureIsNotShownAsAnImageFormatProblem() {
        doThrow(new IllegalStateException("db failure"))
                .when(destinationSaveOrchestrationService)
                .registerDestination(any(), any(), any());

        assertThatThrownBy(() -> mockMvc.perform(multipart("/admin/destinations")
                .param("regionId", "31")
                .param("season", "SPRING")
                .param("type", "ATTRACTION")
                .param("ktoSelectedPhotosJson", "[]")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private HandlerMethodArgumentResolver principalResolver(CustomUserDetails principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
