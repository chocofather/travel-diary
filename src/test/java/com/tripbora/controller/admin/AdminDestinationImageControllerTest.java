package com.tripbora.controller.admin;

import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.service.destination.DestinationCommonsImageManagementService;
import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.destination.DestinationKtoImageManagementService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.file.UnsupportedImageFormatException;
import com.tripbora.service.kto.InvalidKtoSelectedPhotosException;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDestinationImageControllerTest {

    @Mock private DestinationImageService destinationImageService;
    @Mock private DestinationService destinationService;
    @Mock private KtoSelectedPhotoRequestParser requestParser;
    @Mock private DestinationKtoImageManagementService ktoImageManagementService;
    @Mock private DestinationCommonsImageManagementService commonsImageManagementService;

    private AdminDestinationImageController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AdminDestinationImageController(
                destinationImageService,
                destinationService,
                requestParser,
                ktoImageManagementService,
                commonsImageManagementService,
                new com.tripbora.service.file.DestinationCardThumbnailService("build/tmp/no-uploads",
                        org.mockito.Mockito.mock(DestinationImageService.class)));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * 관리 화면도 공공누리 제3유형(변경금지)은 썸네일 파일 대신 원본을 쓴다.
     * 예전에 만들어 둔 3유형 썸네일이 남아 있어도 쓰지 않고, 1유형은 기존처럼 만들어 둔 썸네일을 쓴다.
     */
    @Test
    void imageManagementUsesOriginalsForNoDerivativesPhotos(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
            throws Exception {
        for (String name : List.of("kogl1.jpg", "kogl3.jpg")) {
            java.nio.file.Path original = root.resolve("destinations").resolve(name);
            java.nio.file.Path legacy = root.resolve("thumbnail-cache/destinations/v2/480").resolve(name);
            java.nio.file.Files.createDirectories(original.getParent());
            java.nio.file.Files.createDirectories(legacy.getParent());
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1200, 900,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", original.toFile());
            java.nio.file.Files.copy(original, legacy);
        }
        // 두 사진 모두 썸네일 파일이 이미 있어 미리 만들기는 라이선스를 묻지 않는다. 화면은 사진의 라이선스로 고른다.
        DestinationImageService licenses = org.mockito.Mockito.mock(DestinationImageService.class);
        AdminDestinationImageController screen = new AdminDestinationImageController(
                destinationImageService, destinationService, requestParser, ktoImageManagementService,
                commonsImageManagementService,
                new com.tripbora.service.file.DestinationCardThumbnailService(root.toString(), licenses));
        DestinationImage type1 = image(1L, "/uploads/destinations/kogl1.jpg", "KOGL_TYPE_1");
        DestinationImage type3 = image(3L, "/uploads/destinations/kogl3.jpg", "KOGL_TYPE_3");
        when(destinationImageService.getImages(10L)).thenReturn(List.of(type1, type3));
        when(destinationService.getTranslationsByDestinationId(10L)).thenReturn(List.of(translation("ko", "경복궁")));
        ExtendedModelMap model = new ExtendedModelMap();

        screen.showImageUploadForm(10L, model);

        @SuppressWarnings("unchecked")
        java.util.Map<Long, String> thumbnails = (java.util.Map<Long, String>) model.get("imageThumbnails");
        assertThat(thumbnails).containsOnlyKeys(1L);
        assertThat(type3.isNoDerivatives()).isTrue();
    }

    private DestinationImage image(Long id, String imageUrl, String licenseType) {
        DestinationImage image = new DestinationImage();
        image.setId(id);
        image.setImageUrl(imageUrl);
        image.setLicenseType(licenseType);
        return image;
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

        String view = controller.uploadImages(
                10L,
                new MockMultipartFile[]{first, second},
                new ExtendedModelMap(),
                new org.springframework.mock.web.MockHttpServletResponse());

        assertThat(view).isEqualTo("redirect:/admin/destinations/10/images");
        verify(destinationImageService).saveImages(
                eq(10L),
                eq(new MockMultipartFile[]{first, second}),
                eq(null),
                eq(new Integer[0]));
    }

    @Test
    void directUploadSubmitsThreeAppliedAndIndividuallyEditedSourceValues() throws Exception {
        mockMvc.perform(multipart("/admin/destinations/10/images")
                        .file(image("first.jpg"))
                        .file(image("second.jpg"))
                        .file(image("third.jpg"))
                        .param("imageSourceNames", "공통 기관", "공통 기관", "공통 기관")
                        .param("imagePhotographers", "공통 촬영자", "개별 촬영자", "수정한 촬영자")
                        .param("imageCommonSourceUrls", "https://example.org/source",
                                "https://example.org/source", "https://example.org/source"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images"));

        verify(destinationImageService).saveImages(eq(10L),
                any(org.springframework.web.multipart.MultipartFile[].class), eq(null), eq(new Integer[0]),
                eq(new String[]{"공통 기관", "공통 기관", "공통 기관"}),
                eq(new String[]{"공통 촬영자", "개별 촬영자", "수정한 촬영자"}),
                eq(null), eq(null), eq(null),
                eq(new String[]{"https://example.org/source", "https://example.org/source",
                        "https://example.org/source"}), eq(null));
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
    void imageMetadataCanBeUpdatedWithoutUploadingAReplacement() {
        String view = controller.updateImageMetadata(
                10L, 2L, "한국관광공사", "한국관광공사 김지호",
                "CREATIVE_COMMONS", "CC BY 4.0", "https://example.com/source");

        assertThat(view).isEqualTo("redirect:/admin/destinations/10/images");
        verify(destinationImageService).updateImageMetadata(
                10L, 2L, "한국관광공사", "한국관광공사 김지호",
                "CREATIVE_COMMONS", "CC BY 4.0", "https://example.com/source");
    }

    @Test
    void bulkSourceEndpointPassesSelectedImagesAndExplicitConfirmations() throws Exception {
        when(destinationImageService.applyBulkSource(10L, List.of(2L, 3L),
                "공공기관", null, "KOGL_TYPE_1", null,
                "https://example.com/collection", java.util.Set.of("sourceName"), true, true))
                .thenReturn(new DestinationImageService.BulkSourceResult(2, 1, List.of(2L)));

        mockMvc.perform(post("/admin/destinations/10/images/sources/bulk")
                        .param("imageIds", "2", "3")
                        .param("sourceName", "공공기관")
                        .param("licenseType", "KOGL_TYPE_1")
                        .param("commonSourceUrl", "https://example.com/collection")
                        .param("overwriteFields", "sourceName")
                        .param("licenseConfirmed", "true")
                        .param("overwriteConfirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#registered-images"))
                .andExpect(flash().attributeExists("bulkSourceResult"));

        verify(destinationImageService).applyBulkSource(10L, List.of(2L, 3L),
                "공공기관", null, "KOGL_TYPE_1", null,
                "https://example.com/collection", java.util.Set.of("sourceName"), true, true);
    }

    @Test
    void wikidataDestinationPageExposesQidAndAlreadyRegisteredCommonsFiles() {
        List<DestinationImage> images = List.of(new DestinationImage());
        when(destinationImageService.getImages(10L)).thenReturn(images);
        when(commonsImageManagementService.findWikidataQid(10L)).thenReturn("Q243");
        when(commonsImageManagementService.registeredCommonsFileNames(images)).thenReturn(List.of("A.jpg"));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.showImageUploadForm(10L, model);

        assertThat(model.get("wikidataQid")).isEqualTo("Q243");
        assertThat(model.get("registeredCommonsFiles")).isEqualTo(List.of("A.jpg"));
    }

    @Test
    void commonsAddSuccessAndFailuresReturnToTheCommonsSectionWithAMessage() throws Exception {
        String json = "{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"B.jpg\",\"main\":false}]}";
        when(commonsImageManagementService.addPhotos(10L, json))
                .thenReturn(2)
                .thenThrow(new com.tripbora.service.wikidata.CommonsPhotoSelectionException(
                        "다음 Commons 사진은 자동 저장할 수 없습니다. B.jpg: 퍼블릭 도메인"))
                .thenThrow(new com.tripbora.service.wikidata.CommonsPhotoDownloadException(
                        "Commons 사진을 내려받지 못했습니다: B.jpg", null))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("insert failed"));

        mockMvc.perform(post("/admin/destinations/10/images/commons").param("commonsSelectedPhotosJson", json))
                .andExpect(redirectedUrl("/admin/destinations/10/images#commons-add"))
                .andExpect(flash().attribute("commonsAddResult", "Commons 사진 2장을 추가했습니다."));
        mockMvc.perform(post("/admin/destinations/10/images/commons").param("commonsSelectedPhotosJson", json))
                .andExpect(flash().attribute("commonsAddError", "다음 Commons 사진은 자동 저장할 수 없습니다. B.jpg: 퍼블릭 도메인"));
        mockMvc.perform(post("/admin/destinations/10/images/commons").param("commonsSelectedPhotosJson", json))
                .andExpect(flash().attribute("commonsAddError", "Commons 사진을 내려받지 못했습니다: B.jpg"));
        mockMvc.perform(post("/admin/destinations/10/images/commons").param("commonsSelectedPhotosJson", json))
                .andExpect(flash().attribute("commonsAddError",
                        "Commons 사진 저장에 실패했습니다. 선택한 사진은 하나도 저장되지 않았습니다. 다시 시도해 주세요."));
    }

    @Test
    void bulkSourceFailureReturnsToTheVisibleErrorArea() throws Exception {
        when(destinationImageService.applyBulkSource(10L, List.of(2L),
                null, null, null, null, null, null, false, false))
                .thenThrow(new IllegalArgumentException("출처 URL 형식이 올바르지 않습니다."));

        mockMvc.perform(post("/admin/destinations/10/images/sources/bulk")
                        .param("imageIds", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#registered-images"))
                .andExpect(flash().attribute("bulkSourceError", "출처 URL 형식이 올바르지 않습니다."));
    }

    @Test
    void bulkSourceStorageFailureShowsAUsableError() throws Exception {
        when(destinationImageService.applyBulkSource(10L, List.of(2L),
                "공공기관", null, null, null, null, null, false, false))
                .thenThrow(new IllegalStateException("storage unavailable"));

        mockMvc.perform(post("/admin/destinations/10/images/sources/bulk")
                        .param("imageIds", "2")
                        .param("sourceName", "공공기관"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#registered-images"))
                .andExpect(flash().attribute("bulkSourceError", "공통 출처 저장에 실패했습니다. 다시 시도해 주세요."));
    }

    @Test
    void individualPhotoEditKeepsCommonAndWorkPagesSeparateFromLegacyUrl() throws Exception {
        mockMvc.perform(post("/admin/destinations/images/2/metadata")
                        .param("destinationId", "10")
                        .param("sourceName", "공공기관")
                        .param("sourceUrl", "https://example.com/legacy")
                        .param("commonSourceUrl", "https://example.com/collection")
                        .param("workPageUrl", "https://example.com/work/2"))
                .andExpect(status().is3xxRedirection());

        verify(destinationImageService).updateImageMetadataAndPages(10L, 2L,
                "공공기관", null, null, null, "https://example.com/legacy",
                "https://example.com/collection", "https://example.com/work/2");
    }

    /**
     * 출처 저장은 그 사진 카드로 돌아간다. 입력값 검증에 실패해도 오류 페이지로 보내지 않고
     * 이유와 방금 입력한 값을 넘겨 그 카드의 출처 영역을 펼쳐 보이게 한다.
     */
    @Test
    void savingASourceReturnsToThatCardAndARejectedUrlKeepsTheDraftInsteadOfAnErrorPage() throws Exception {
        mockMvc.perform(post("/admin/destinations/images/2/metadata")
                        .param("destinationId", "10")
                        .param("sourceName", "한국관광공사"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#image-2"))
                .andExpect(flash().attribute("metadataSavedImageId", 2L));

        doThrow(new DestinationImageService.InvalidSourceUrlException("출처 URL은 http 또는 https 주소여야 합니다."))
                .when(destinationImageService).updateImageMetadataAndPages(10L, 3L,
                        "공공기관", "김지호", null, null, null, "ftp://bad", null);
        mockMvc.perform(post("/admin/destinations/images/3/metadata")
                        .param("destinationId", "10")
                        .param("sourceName", "공공기관")
                        .param("photographer", "김지호")
                        .param("commonSourceUrl", "ftp://bad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#image-3"))
                .andExpect(flash().attribute("metadataErrorImageId", 3L))
                .andExpect(flash().attribute("metadataError", "출처 URL은 http 또는 https 주소여야 합니다."))
                .andExpect(result -> assertThat((java.util.Map<String, String>) result.getFlashMap().get("metadataDraft"))
                        .containsEntry("sourceName", "공공기관")
                        .containsEntry("photographer", "김지호")
                        .containsEntry("commonSourceUrl", "ftp://bad"));

        // 없는·다른 여행지 사진은 입력 문제가 아니므로 그대로 400 이다.
        doThrow(new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다."))
                .when(destinationImageService).updateImageMetadataAndPages(eq(10L), eq(404L),
                        any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(post("/admin/destinations/images/404/metadata").param("destinationId", "10"))
                .andExpect(status().isBadRequest());
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

        // Whitelabel 400 대신 이미지 관리 화면을 다시 그리고 폼 안에서 이유를 알려준다
        var result = mockMvc.perform(multipart("/admin/destinations/10/images")
                        .file(new MockMultipartFile("files", "fake.jpg", "image/jpeg",
                                "hello".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException()).isNull();
        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName())
                .isEqualTo("admin/destinations/image-upload");
        assertThat(result.getModelAndView().getModel().get("imageError"))
                .isEqualTo("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
        // 화면 재렌더링에 필요한 모델이 그대로 복구된다
        assertThat(result.getModelAndView().getModel()).containsKeys(
                "destinationId", "destinationName", "imageList", "imageCount");
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
