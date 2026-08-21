package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationKtoImageManagementService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDestinationImageControllerTest {

    @Mock private DestinationImageService destinationImageService;
    @Mock private DestinationService destinationService;
    @Mock private KtoSelectedPhotoRequestParser requestParser;
    @Mock private DestinationKtoImageManagementService ktoImageManagementService;

    private AdminDestinationImageController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AdminDestinationImageController(
                destinationImageService,
                destinationService,
                requestParser,
                ktoImageManagementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void imageManagementShowsKoreanNameAndCurrentImageCount() {
        DestinationTranslation english = translation("en", "Gyeongbokgung");
        DestinationTranslation korean = translation("ko", "경복궁");
        List<DestinationImage> images = List.of(new DestinationImage(), new DestinationImage());
        when(destinationImageService.getImages(10L)).thenReturn(images);
        when(destinationService.getTranslationsByDestinationId(10L))
                .thenReturn(List.of(english, korean));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.showImageUploadForm(10L, model);

        assertThat(view).isEqualTo("admin/destinations/image-upload");
        assertThat(model.get("destinationId")).isEqualTo(10L);
        assertThat(model.get("destinationName")).isEqualTo("경복궁");
        assertThat(model.get("imageList")).isSameAs(images);
        assertThat(model.get("imageCount")).isEqualTo(2);
    }

    @Test
    void directUploadAddsEveryFileAsOrdinaryNonSlideImagesAndReturnsToManagement() {
        MockMultipartFile first = image("first.jpg");
        MockMultipartFile second = image("second.jpg");

        String view = controller.uploadImages(10L, new MockMultipartFile[]{first, second});

        assertThat(view).isEqualTo("redirect:/admin/destinations/10/images");
        verify(destinationImageService).saveImages(
                eq(10L),
                eq(new MockMultipartFile[]{first, second}),
                eq(null),
                eq(new Integer[0]));
    }

    @Test
    void selectedKtoPhotosAreParsedThenAddedToTheExistingDestination() {
        String json = "[{\"externalContentId\":\"100\"}]";
        List<KtoSelectedPhotoRequest> selected = List.of(new KtoSelectedPhotoRequest(
                "100",
                "https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                "경복궁",
                "촬영자",
                true));
        when(requestParser.parse(json)).thenReturn(selected);

        String view = controller.addKtoPhotos(10L, json);

        assertThat(view).isEqualTo("redirect:/admin/destinations/10/images");
        verify(ktoImageManagementService).addPhotos(10L, selected);
    }

    @Test
    void malformedKtoSelectionIsRejectedBeforeDownload() {
        when(requestParser.parse("[{")).thenThrow(new InvalidKtoSelectedPhotosException());

        assertThatThrownBy(() -> controller.addKtoPhotos(10L, "[{"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(ktoImageManagementService);
    }

    @Test
    void imageCardActionsDelegateMainSlideAndDeleteOperations() {
        assertThat(controller.setMainImage(10L, 2L))
                .isEqualTo("redirect:/admin/destinations/10/images");
        assertThat(controller.toggleSlideImage(10L, 2L))
                .isEqualTo("redirect:/admin/destinations/10/images");
        assertThat(controller.deleteImage(2L, 10L))
                .isEqualTo("redirect:/admin/destinations/10/images");

        verify(destinationImageService).setMainImage(10L, 2L);
        verify(destinationImageService).toggleSlideImage(10L, 2L);
        // 삭제도 main/slide 와 같은 소유권 계약(destinationId + imageId)을 따른다
        verify(destinationImageService).deleteImage(10L, 2L);
        verify(requestParser, never()).parse(null);
    }

    @Test
    void unknownOrForeignImageDeletionAnswersBadRequestInsteadOfServerError() throws Exception {
        doThrow(new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다."))
                .when(destinationImageService).deleteImage(100L, 404L);
        doThrow(new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다."))
                .when(destinationImageService).deleteImage(100L, 2001L);

        // 존재하지 않는 imageId
        mockMvc.perform(post("/admin/destinations/images/404/delete")
                        .param("destinationId", "100"))
                .andExpect(status().isBadRequest());
        // 다른 여행지 소속 imageId
        mockMvc.perform(post("/admin/destinations/images/2001/delete")
                        .param("destinationId", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedDirectUploadAnswersBadRequestWithAGuidingMessage() throws Exception {
        doThrow(new UnsupportedImageFormatException(
                "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다."))
                .when(destinationImageService).saveImages(
                        eq(10L),
                        any(org.springframework.web.multipart.MultipartFile[].class),
                        eq((Integer) null),
                        any(Integer[].class));

        var result = mockMvc.perform(multipart("/admin/destinations/10/images")
                        .file(new MockMultipartFile("files", "fake.jpg", "image/jpeg",
                                "hello".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
        assertThat(result.getResolvedException().getMessage())
                .contains("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
    }

    @Test
    void unknownOrForeignMainImageSelectionAnswersBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다."))
                .when(destinationImageService).setMainImage(100L, 2001L);

        mockMvc.perform(post("/admin/destinations/images/2001/main")
                        .param("destinationId", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrForeignSlideToggleAnswersBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다."))
                .when(destinationImageService).toggleSlideImage(100L, 2001L);

        mockMvc.perform(post("/admin/destinations/images/2001/slide")
                        .param("destinationId", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validImageCardActionsStillRedirectToTheManagementPage() throws Exception {
        mockMvc.perform(post("/admin/destinations/images/2/delete").param("destinationId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images"));
        mockMvc.perform(post("/admin/destinations/images/2/main").param("destinationId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images"));
        mockMvc.perform(post("/admin/destinations/images/2/slide").param("destinationId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images"));

        verify(destinationImageService).deleteImage(10L, 2L);
        verify(destinationImageService).setMainImage(10L, 2L);
        verify(destinationImageService).toggleSlideImage(10L, 2L);
    }

    @Test
    void unexpectedServiceFailureIsNotHiddenBehindBadRequest() {
        doThrow(new IllegalStateException("db failure"))
                .when(destinationImageService).deleteImage(10L, 2L);

        assertThatThrownBy(() -> mockMvc.perform(
                post("/admin/destinations/images/2/delete").param("destinationId", "10")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private DestinationTranslation translation(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1});
    }
}
