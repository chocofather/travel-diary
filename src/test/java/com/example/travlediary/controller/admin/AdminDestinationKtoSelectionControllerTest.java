package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
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
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    private AdminDestinationController controller;

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
                destinationSaveOrchestrationService
        );
    }

    @Test
    void rejectsMalformedRegistrationJsonBeforeCallingDestinationService() {
        DestinationForm form = new DestinationForm();
        form.setKtoSelectedPhotosJson("[{");

        assertBadRequest(() -> controller.registerDestination(form, null));
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

        String view = controller.updateDestination(9L, form);

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

        String view = controller.registerDestination(form, userDetails);

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

        String view = controller.updateDestination(9L, form);

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

        assertBadRequest(() -> controller.registerDestination(form, userDetails));
    }

    private void assertBadRequest(Runnable request) {
        assertThatThrownBy(request::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("선택한 관광사진 정보가 올바르지 않습니다.");
    }
}
