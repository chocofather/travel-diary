package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@ExtendWith(MockitoExtension.class)
class AdminDestinationKtoSelectionControllerTest {

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

    private AdminDestinationController controller;
    private MockMvc mockMvc;
    private CustomUserDetails authenticatedAdmin;

    @BeforeEach
    void setUp() {
        KtoSelectedPhotoRequestParser parser = new KtoSelectedPhotoRequestParser(
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        controller = new AdminDestinationController(
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
        authenticatedAdmin = mock(CustomUserDetails.class);
        lenient().when(authenticatedAdmin.getId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .setCustomArgumentResolvers(authenticationPrincipalResolver(authenticatedAdmin))
                .build();
    }

    @Test
    void missingRegionReturnsThePopulatedCreateFormWithoutCallingPersistence() throws Exception {
        var result = mockMvc.perform(multipart("/admin/destinations")
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].name", "창덕궁")
                        .param("translations[0].description", "TourAPI로 채운 설명")
                        .param("latitude", "37.579")
                        .param("longitude", "126.991")
                        .param("ktoSelectedPhotosJson", "[]"))
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin/destinations/create");
        assertThat(result.getModelAndView().getModel()).containsKey("destinationForm");
        DestinationForm submittedForm = (DestinationForm) result.getModelAndView()
                .getModel().get("destinationForm");
        assertThat(submittedForm.getTranslations().get(0).getName()).isEqualTo("창덕궁");
        assertThat(submittedForm.getTranslations().get(0).getDescription()).isEqualTo("TourAPI로 채운 설명");
        assertThat(submittedForm.getLatitude()).isEqualByComparingTo("37.579");
        assertThat(submittedForm.getLongitude()).isEqualByComparingTo("126.991");
        BindingResult bindingResult = (BindingResult) result.getModelAndView().getModel()
                .get("org.springframework.validation.BindingResult.destinationForm");
        assertThat(bindingResult.hasFieldErrors("regionId")).isTrue();
        assertThat(bindingResult.getFieldError("regionId").getDefaultMessage())
                .isEqualTo("지역을 선택해 주세요.");
        verifyNoInteractions(destinationSaveOrchestrationService);
    }

    @Test
    void selectedRegionKeepsTheExistingRegistrationFlow() throws Exception {
        var result = mockMvc.perform(multipart("/admin/destinations")
                        .param("regionId", "9")
                        .param("ktoSelectedPhotosJson", "[]"))
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/admin");
        verify(destinationSaveOrchestrationService).registerDestination(
                argThat(form -> Long.valueOf(9L).equals(form.getRegionId())),
                eq(7L),
                eq(java.util.List.of()));
    }

    @Test
    void rejectedDirectImageUploadAnswersBadRequestInsteadOfServerError() throws Exception {
        org.mockito.Mockito.doThrow(
                        new com.example.travlediary.service.file.UnsupportedImageFormatException(
                                "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다."))
                .when(destinationSaveOrchestrationService)
                .registerDestination(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        var result = mockMvc.perform(multipart("/admin/destinations")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "images", "fake.jpg", "image/jpeg",
                                "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("regionId", "9")
                        .param("ktoSelectedPhotosJson", "[]"))
                .andReturn();

        // 400 은 유지하되 Whitelabel 대신 등록 폼을 다시 그린다
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResolvedException()).isNull();
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin/destinations/create");
        assertThat(result.getModelAndView().getModel().get("imageError"))
                .isEqualTo("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
    }

    @Test
    void rejectsMalformedRegistrationJsonBeforeCallingDestinationService() {
        DestinationForm form = new DestinationForm();
        form.setKtoSelectedPhotosJson("[{");

        assertBadRequest(() -> register(form, null));
        verifyNoInteractions(destinationService);
        verifyNoInteractions(destinationSaveOrchestrationService);
    }

    @Test
    void informationOnlyUpdateIgnoresManipulatedImageSelectionPayload() {
        DestinationForm form = new DestinationForm();
        form.setKtoSelectedPhotosJson("""
                [
                  {"externalContentId":"100","imageUrl":"https://images.example.test/a.jpg","isMain":true},
                  {"externalContentId":"101","imageUrl":"https://images.example.test/b.jpg","isMain":true}
                ]
                """);

        String view = update(form);

        assertThat(view).isEqualTo("redirect:/admin/destinations");
        verify(destinationService).updateDestination(9L, form);
        verifyNoInteractions(destinationSaveOrchestrationService);
    }

    @Test
    void keepsExistingMultipartImagesWhenAnEmptyKtoSelectionIsValid() {
        DestinationForm form = new DestinationForm();
        MockMultipartFile image = new MockMultipartFile(
                "images", "palace.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        form.setImages(new MockMultipartFile[]{image});
        form.setKtoSelectedPhotosJson("[]");
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getId()).thenReturn(7L);

        String view = register(form, userDetails);

        assertThat(view).isEqualTo("redirect:/admin");
        assertThat(form.getImages()).containsExactly(image);
        verify(destinationSaveOrchestrationService)
                .registerDestination(form, 7L, java.util.List.of());
        verifyNoInteractions(destinationService);
    }

    @Test
    void informationOnlyUpdateDoesNotSendKtoSelectionsToSaveOrchestration() {
        DestinationForm form = new DestinationForm();
        form.setKtoSelectedPhotosJson("""
                [{
                  "externalContentId":"100",
                  "imageUrl":"https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                  "title":"경복궁",
                  "photographer":"촬영자",
                  "isMain":true
                }]
                """);

        String view = update(form);

        assertThat(view).isEqualTo("redirect:/admin/destinations");
        verify(destinationService).updateDestination(9L, form);
        verifyNoInteractions(destinationSaveOrchestrationService);
    }

    @Test
    void convertsCreateMainConflictToSafeBadRequest() {
        DestinationForm form = new DestinationForm();
        form.setKtoSelectedPhotosJson("[]");
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getId()).thenReturn(7L);
        doThrow(new InvalidKtoSelectedPhotosException())
                .when(destinationSaveOrchestrationService)
                .registerDestination(form, 7L, java.util.List.of());

        assertBadRequest(() -> register(form, userDetails));
    }

    private void assertBadRequest(Runnable request) {
        assertThatThrownBy(request::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("선택한 관광사진 정보가 올바르지 않습니다.");
    }

    private String register(DestinationForm form, CustomUserDetails userDetails) {
        return controller.registerDestination(
                form,
                new BeanPropertyBindingResult(form, "destinationForm"),
                userDetails,
                new ExtendedModelMap(),
                new org.springframework.mock.web.MockHttpServletResponse(),
                "ko"
        );
    }

    private String update(DestinationForm form) {
        return controller.updateDestination(
                9L,
                form,
                new BeanPropertyBindingResult(form, "destinationForm"),
                new ExtendedModelMap(),
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap(),
                "ko"
        );
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver(CustomUserDetails principal) {
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
