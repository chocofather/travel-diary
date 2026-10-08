package com.tripbora.service.destination;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripbora.model.DestinationImage;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationImageServiceTest {

    @Mock private DestinationMapper destinationMapper;
    @Mock private FileUploadService fileUploadService;

    @TempDir
    Path uploadDir;

    private DestinationImageService service;

    @BeforeEach
    void setUp() {
        service = new DestinationImageService(destinationMapper, fileUploadService);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
        AtomicLong nextImageId = new AtomicLong(100L);
        doAnswer(invocation -> {
            DestinationImage image = invocation.getArgument(0);
            image.setId(nextImageId.getAndIncrement());
            return null;
        }).when(destinationMapper).insertImage(any());
    }

    @Test
    void appendsMultipleImagesAfterCurrentMaximumInOrder() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 1, true),
                image(2L, 10L, 4, false)
        ));
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new-a.jpg")
                .thenReturn("/uploads/destinations/new-b.jpg");

        service.saveImages(10L, files("a.jpg", "b.jpg"), null, new Integer[0]);

        assertThat(insertedImages())
                .extracting(DestinationImage::getOrderIndex)
                .containsExactly(5, 6);
    }

    @Test
    void selectingNewMainClearsExistingMainAndMarksOnlySelectedImage() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 0, true)
        ));
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new-a.jpg")
                .thenReturn("/uploads/destinations/new-b.jpg")
                .thenReturn("/uploads/destinations/new-c.jpg");

        service.saveImages(10L, files("a.jpg", "b.jpg", "c.jpg"), 1, new Integer[0]);

        assertThat(invocationsNamed("clearMainImagesByDestinationId"))
                .singleElement()
                .satisfies(invocation -> assertThat((Long) invocation.getArgument(0)).isEqualTo(10L));
        assertThat(insertedImages())
                .extracting(DestinationImage::getIsMain)
                .containsExactly(false, true, false);
    }

    @Test
    void addingImagesWithoutNewMainLeavesExistingMainUntouched() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 3, true)
        ));
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new.jpg");

        service.saveImages(10L, files("new.jpg"), null, new Integer[0]);

        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
        assertThat(insertedImages())
                .extracting(DestinationImage::getIsMain)
                .containsExactly(false);
    }

    @Test
    void addingNonMainImageToEmptyDestinationKeepsItWithoutMainAtOrderZero() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/first.jpg");

        service.saveImages(10L, files("first.jpg"), null, new Integer[0]);

        assertThat(insertedImages())
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getOrderIndex()).isZero();
                    assertThat(image.getIsMain()).isFalse();
                });
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
        assertThat(invocationsNamed("setMainImage")).isEmpty();
    }

    @Test
    void directUploadsKeepSourceMetadataAndLicenseDetailsPerImage() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/a.jpg")
                .thenReturn("/uploads/destinations/b.jpg");

        service.saveImages(
                10L,
                files("a.jpg", "b.jpg"),
                null,
                new Integer[0],
                new String[]{"한국관광공사", "서울특별시"},
                new String[]{"김지호", "박하늘"},
                new String[]{"KOGL_TYPE_1", "CREATIVE_COMMONS"},
                new String[]{null, "CC BY 4.0"},
                new String[]{"https://example.com/a", "https://example.com/b"});

        assertThat(insertedImages())
                .extracting(DestinationImage::getSourceName,
                        DestinationImage::getPhotographer,
                        DestinationImage::getLicenseType,
                        DestinationImage::getLicenseDetail,
                        DestinationImage::getSourceUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "한국관광공사", "김지호", "KOGL_TYPE_1", null,
                                "https://example.com/a"),
                        org.assertj.core.groups.Tuple.tuple(
                                "서울특별시", "박하늘", "CREATIVE_COMMONS", "CC BY 4.0",
                                "https://example.com/b"));
        assertThat(invocationsNamed("insertImageSource")).hasSize(2);
        assertThat(invocationsNamed("insertImageSource"))
                .extracting(invocation -> ((DestinationImage) invocation.getArgument(0)).getId())
                .containsExactly(100L, 101L);
        assertThat(mockingDetails(destinationMapper).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .filter(name -> name.equals("insertImage") || name.equals("insertImageSource"))
                .toList())
                .containsExactly("insertImage", "insertImageSource", "insertImage", "insertImageSource");
    }

    @Test
    void directUploadWithoutSourceMetadataKeepsAllMetadataNull() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/existing-compatible.jpg");

        service.saveImages(10L, files("existing-compatible.jpg"), null, new Integer[0]);

        assertThat(insertedImages())
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getSourceName()).isNull();
                    assertThat(image.getLicenseType()).isNull();
                    assertThat(image.getSourceUrl()).isNull();
                });
        assertThat(invocationsNamed("insertImageSource")).isEmpty();
    }

    @Test
    void directUploadStoresCommonPageAndWorkPageIndependentlyInItsOwnSourceRow() {
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new.jpg");

        service.saveImages(10L, files("new.jpg"), null, new Integer[0],
                new String[]{"공공기관"}, null, null, null, null,
                new String[]{"https://example.com/collection"},
                new String[]{"https://example.com/work/1"});

        assertThat(insertedImages()).singleElement().satisfies(image -> {
            assertThat(image.getCommonSourceUrl()).isEqualTo("https://example.com/collection");
            assertThat(image.getWorkPageUrl()).isEqualTo("https://example.com/work/1");
            assertThat(image.getSourceUrl()).isNull();
        });
        assertThat(invocationsNamed("insertImageSource")).hasSize(1);
    }

    @Test
    void ktoImageAlsoWritesItsOwnSourceAfterTheImage() {
        DestinationImage ktoImage = image(null, 10L, 0, true);
        ktoImage.setSourceType("KTO_TOURAPI");
        ktoImage.setSourceName("한국관광공사");
        ktoImage.setExternalContentId("12345");
        ktoImage.setLicenseType("KOGL_TYPE_3");

        service.saveImages(10L, List.of(ktoImage));

        assertThat(invocationsNamed("insertImageSource"))
                .singleElement()
                .satisfies(invocation -> {
                    DestinationImage saved = invocation.getArgument(0);
                    assertThat(saved.getId()).isEqualTo(100L);
                    assertThat(saved.getSourceType()).isEqualTo("KTO_TOURAPI");
                    assertThat(saved.getExternalContentId()).isEqualTo("12345");
                    assertThat(saved.getLicenseType()).isEqualTo("KOGL_TYPE_3");
                });
    }

    /**
     * 관리 화면의 나눠 올리기: 사진 한 장이 한 번의 저장이다. 기존 사진 다음 순서로 붙고, 출처는 그 사진에 연결되며,
     * 대표·슬라이드는 건드리지 않는다. 실패하면 그 사진 파일만 지우고 앞서 저장된 사진은 남는다.
     */
    @Test
    void oneUploadedPhotoIsSavedAfterExistingImagesWithItsOwnSourceAndCleansUpOnlyItselfOnFailure() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(image(1L, 10L, 7, true)));
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/one.jpg")
                .thenReturn("/uploads/destinations/broken-source.jpg");

        Long imageId = service.saveUploadedImage(10L, files("one.jpg")[0],
                " 한국관광공사 ", "김지호", "KOGL_TYPE_1", null, null,
                "https://kto.visitkorea.or.kr", " ");

        assertThat(imageId).isEqualTo(100L);
        assertThat(insertedImages()).singleElement().satisfies(saved -> {
            assertThat(saved.getImageUrl()).isEqualTo("/uploads/destinations/one.jpg");
            assertThat(saved.getOrderIndex()).isEqualTo(8);
            assertThat(saved.getIsMain()).isFalse();
            assertThat(saved.getIsSlide()).isFalse();
            assertThat(saved.getSourceName()).isEqualTo("한국관광공사");
            assertThat(saved.getWorkPageUrl()).isNull();
        });
        verify(destinationMapper, never()).clearMainImagesByDestinationId(any());
        assertThat(invocationsNamed("insertImageSource")).hasSize(1);

        // 잘못된 출처 URL: 이 사진만 실패하고 저장한 파일은 되돌린다.
        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.saveUploadedImage(10L, files("broken.jpg")[0],
                    null, null, null, null, null, "javascript:alert(1)", null))
                    .isInstanceOf(DestinationImageService.InvalidSourceUrlException.class);
            completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);
        });
        verify(fileUploadService).deleteDestinationFile("/uploads/destinations/broken-source.jpg");
        verify(fileUploadService, never()).deleteDestinationFile("/uploads/destinations/one.jpg");
    }

    /**
     * 순서 편집 저장: 요청한 순서대로 0부터 다시 매기고, 자리가 바뀐 사진만 고친다.
     * 대표·슬라이드 지정은 순서와 상관없어 건드리지 않는다.
     */
    @Test
    void savedImageOrderRenumbersOnlyMovedPhotosAndKeepsMainAndSlide() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 0, true), image(2L, 10L, 1, false), image(3L, 10L, 2, false), image(4L, 10L, 3, false)));

        // 4번째 사진을 2번째 자리로 (1, 4, 2, 3)
        service.saveImageOrder(10L, List.of(1L, 4L, 2L, 3L));

        assertThat(orderUpdates()).containsExactly(List.of(4L, 1), List.of(2L, 2), List.of(3L, 3));
        verify(destinationMapper, never()).clearMainImagesByDestinationId(any());
        verify(destinationMapper, never()).setMainImage(any());
        assertThat(invocationsNamed("updateImageSlide")).isEmpty();
    }

    /** 다른 여행지 사진, 중복, 빠진 사진이 있으면 아무것도 바꾸지 않는다. */
    @Test
    void imageOrderMustListExactlyThisDestinationsPhotos() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 0, true), image(2L, 10L, 1, false), image(3L, 10L, 2, false)));

        for (List<Long> invalid : List.of(
                List.of(1L, 2L, 99L),          // 다른 여행지 사진
                List.of(1L, 2L, 2L),           // 중복
                List.of(1L, 2L),               // 빠진 사진
                List.of(1L, 2L, 3L, 4L),       // 그 사이 지워진 사진
                List.<Long>of())) {
            assertThatThrownBy(() -> service.saveImageOrder(10L, invalid))
                    .as(invalid.toString())
                    .isInstanceOf(DestinationImageService.InvalidImageOrderException.class);
        }
        assertThat(invocationsNamed("updateImageOrder")).isEmpty();
    }

    @Test
    void sourceInsertFailureKeepsDirectUploadRollbackCleanup() {
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new-with-source.jpg");
        doThrow(new IllegalStateException("source insert failed"))
                .when(destinationMapper).insertImageSource(any());

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.saveImages(
                    10L, files("with-source.jpg"), null, new Integer[0],
                    new String[]{"제공기관"}, null, null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("source insert failed");
            completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);
        });

        verify(fileUploadService).deleteDestinationFile(
                "/uploads/destinations/new-with-source.jpg");
    }

    @Test
    void metadataBatchForcesDestinationAndAppendsInOrderUsingExistingMainRule() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 4, true)
        ));
        DestinationImage first = image(null, 999L, 99, false);
        first.setImageUrl("/uploads/destinations/kto-a.jpg");
        DestinationImage second = image(null, 888L, 88, true);
        second.setImageUrl("/uploads/destinations/kto-b.jpg");

        service.saveImages(10L, List.of(first, second));

        assertThat(insertedImages())
                .extracting(
                        DestinationImage::getImageUrl,
                        DestinationImage::getDestinationId,
                        DestinationImage::getOrderIndex,
                        DestinationImage::getIsMain)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "/uploads/destinations/kto-a.jpg", 10L, 5, false),
                        org.assertj.core.groups.Tuple.tuple(
                                "/uploads/destinations/kto-b.jpg", 10L, 6, true));
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).hasSize(1);
    }

    @Test
    void metadataBatchKeepsNonMainFirstImageWithoutPromotionAtOrderZero() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        DestinationImage first = image(null, 999L, 99, false);

        service.saveImages(10L, List.of(first));

        assertThat(insertedImages())
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getDestinationId()).isEqualTo(10L);
                    assertThat(image.getOrderIndex()).isZero();
                    assertThat(image.getIsMain()).isFalse();
                });
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
        assertThat(invocationsNamed("setMainImage")).isEmpty();
    }

    @Test
    void outerTransactionRollbackDeletesOnlyNewDirectUpload() {
        String newImageUrl = "/uploads/destinations/new-direct.jpg";
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any())).thenReturn(newImageUrl);

        withTransactionSynchronization(() -> {
            service.saveImages(10L, files("new-direct.jpg"), null, new Integer[0]);

            completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);
        });

        verify(fileUploadService).deleteDestinationFile(newImageUrl);
    }

    @Test
    void successfulOuterTransactionKeepsNewDirectUpload() {
        String newImageUrl = "/uploads/destinations/new-direct.jpg";
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any())).thenReturn(newImageUrl);

        withTransactionSynchronization(() -> {
            service.saveImages(10L, files("new-direct.jpg"), null, new Integer[0]);

            completeSynchronizations(TransactionSynchronization.STATUS_COMMITTED);
        });

        verify(fileUploadService, never()).deleteDestinationFile(newImageUrl);
    }

    @Test
    void rollbackCleanupFailureDoesNotEscapeTransactionCompletion() {
        String newImageUrl = "/uploads/destinations/new-direct.jpg";
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        when(fileUploadService.saveDestinationImage(any())).thenReturn(newImageUrl);
        doThrow(new RuntimeException("cleanup failed"))
                .when(fileUploadService).deleteDestinationFile(newImageUrl);

        withTransactionSynchronization(() -> {
            service.saveImages(10L, files("new-direct.jpg"), null, new Integer[0]);

            assertThatCode(() -> completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void deletingMainImageReordersRemainingImagesAndPromotesFirst() {
        DestinationImage deleted = image(1L, 10L, 0, true);
        when(destinationMapper.findImageById(1L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(2L, 10L, 4, false),
                image(3L, 10L, 8, false)
        ));

        service.deleteImage(10L, 1L);

        assertThat(orderUpdates()).containsExactly(List.of(2L, 0), List.of(3L, 1));
        assertThat(invocationsNamed("setMainImage"))
                .singleElement()
                .satisfies(invocation -> assertThat((Long) invocation.getArgument(0)).isEqualTo(2L));
    }

    @Test
    void deletingOrdinaryImageReordersImagesAndKeepsCurrentMain() {
        DestinationImage deleted = image(2L, 10L, 3, false);
        when(destinationMapper.findImageById(2L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 2, true),
                image(3L, 10L, 7, false)
        ));

        service.deleteImage(10L, 2L);

        assertThat(orderUpdates()).containsExactly(List.of(1L, 0), List.of(3L, 1));
        assertThat(invocationsNamed("setMainImage")).isEmpty();
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
    }

    /** 선택 삭제는 단건 삭제 규칙을 그대로 따른다: 대표가 지워지면 남은 사진 중 첫 사진이 대표가 된다. */
    @Test
    void bulkDeleteIncludingMainKeepsUnselectedImagesAndPromotesFirstRemaining() {
        List<DestinationImage> stored = storedImages(
                image(1L, 10L, 0, true), image(2L, 10L, 1, false),
                image(3L, 10L, 2, false), image(4L, 10L, 3, false));

        withTransactionSynchronization(() -> {
            assertThat(service.deleteImages(10L, List.of(1L, 2L))).isEqualTo(2);
            commitSynchronizations();
        });

        assertThat(stored).extracting(DestinationImage::getId).containsExactly(3L, 4L);
        assertThat(stored).extracting(DestinationImage::getOrderIndex).containsExactly(0, 1);
        assertThat(stored).extracting(DestinationImage::getIsMain).containsExactly(true, false);
        verify(fileUploadService).deleteDestinationFile("/uploads/destinations/1.jpg");
        verify(fileUploadService).deleteDestinationFile("/uploads/destinations/2.jpg");
        verify(fileUploadService, never()).deleteDestinationFile("/uploads/destinations/3.jpg");
    }

    @Test
    void bulkDeleteOfEveryImageLeavesNoMain() {
        List<DestinationImage> stored = storedImages(image(1L, 10L, 0, true), image(2L, 10L, 1, false));

        service.deleteImages(10L, List.of(2L, 1L));

        assertThat(stored).isEmpty();
        assertThat(invocationsNamed("setMainImage")).isEmpty();
    }

    /** 이미 지워진(중복 제출) 또는 다른 여행지 사진이 섞이면 아무것도 지우지 않는다. */
    @Test
    void bulkDeleteWithForeignOrMissingImageDeletesNothing() {
        List<DestinationImage> stored = storedImages(image(1L, 10L, 0, true), image(2L, 10L, 1, false));

        assertThatThrownBy(() -> service.deleteImages(10L, List.of(2L, 99L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deleteImages(10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stored).hasSize(2);
        assertThat(invocationsNamed("deleteImageById")).isEmpty();
        verifyNoInteractions(fileUploadService);
    }

    /** 여행지 10의 사진 목록처럼 동작하는 mapper 대역. 삭제·순서·대표 변경이 목록에 반영된다. */
    private List<DestinationImage> storedImages(DestinationImage... images) {
        List<DestinationImage> stored = new java.util.ArrayList<>(List.of(images));
        when(destinationMapper.findImagesByDestinationId(10L)).thenAnswer(invocation -> stored.stream()
                .sorted(java.util.Comparator.comparing(DestinationImage::getOrderIndex)).toList());
        when(destinationMapper.findImageById(anyLong())).thenAnswer(invocation -> stored.stream()
                .filter(image -> image.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        doAnswer(invocation -> {
            stored.removeIf(image -> image.getId().equals(invocation.getArgument(0)));
            return null;
        }).when(destinationMapper).deleteImageById(anyLong());
        doAnswer(invocation -> {
            stored.stream().filter(image -> image.getId().equals(invocation.getArgument(0)))
                    .forEach(image -> image.setOrderIndex(invocation.getArgument(1)));
            return null;
        }).when(destinationMapper).updateImageOrder(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        doAnswer(invocation -> {
            stored.forEach(image -> image.setIsMain(false));
            return null;
        }).when(destinationMapper).clearMainImagesByDestinationId(10L);
        doAnswer(invocation -> {
            stored.stream().filter(image -> image.getId().equals(invocation.getArgument(0)))
                    .forEach(image -> image.setIsMain(true));
            return null;
        }).when(destinationMapper).setMainImage(anyLong());
        return stored;
    }

    @Test
    void individualImageFileIsDeletedOnlyAfterCommit() {
        DestinationImage deleted = image(2L, 10L, 0, false);
        when(destinationMapper.findImageById(2L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());

        withTransactionSynchronization(() -> {
            service.deleteImage(10L, 2L);

            verify(fileUploadService, never()).deleteDestinationFile(deleted.getImageUrl());
            commitSynchronizations();
        });

        verify(fileUploadService).deleteDestinationFile(deleted.getImageUrl());
    }

    @Test
    void individualImageDatabaseFailureKeepsExistingFileOnRollback() throws Exception {
        DestinationImage deleted = image(2L, 10L, 0, false);
        Path destinations = Files.createDirectories(uploadDir.resolve("destinations"));
        Path existingFile = Files.write(destinations.resolve("2.jpg"), new byte[]{1, 2, 3});
        when(destinationMapper.findImageById(2L)).thenReturn(deleted);
        doThrow(new IllegalStateException("db failure"))
                .when(destinationMapper).deleteImageById(2L);

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.deleteImage(10L, 2L))
                    .isInstanceOf(IllegalStateException.class);
            completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);
        });

        assertThat(existingFile).exists();
        verify(fileUploadService, never()).deleteDestinationFile(deleted.getImageUrl());
    }

    @Test
    void committedFileDeletionFailureIsLoggedWithoutEscapingCompletion() {
        DestinationImage deleted = image(2L, 10L, 0, false);
        when(destinationMapper.findImageById(2L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of());
        doThrow(new RuntimeException("filesystem failure"))
                .when(fileUploadService).deleteDestinationFile(deleted.getImageUrl());
        Logger logger = (Logger) LoggerFactory.getLogger(DestinationImageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            withTransactionSynchronization(() -> {
                service.deleteImage(10L, 2L);

                assertThatCode(this::commitSynchronizations).doesNotThrowAnyException();
            });
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage())
                            .contains("여행지 이미지 파일을 정리하지 못했습니다");
                });
    }

    @Test
    void settingMainFromImageCardUsesExistingSingleMainRule() {
        DestinationImage selected = image(2L, 10L, 3, false);
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        service.setMainImage(10L, 2L);

        verify(destinationMapper).clearMainImagesByDestinationId(10L);
        verify(destinationMapper).setMainImage(2L);
    }

    @Test
    void togglingSlideFromImageCardPersistsTheOppositeState() {
        DestinationImage selected = image(2L, 10L, 3, false);
        selected.setIsSlide(true);
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        service.toggleSlideImage(10L, 2L);

        verify(destinationMapper).updateImageSlide(2L, false);
    }

    @Test
    void metadataUpdateDoesNotTouchImageStateOrFile() {
        DestinationImage selected = image(2L, 10L, 3, true);
        selected.setIsSlide(true);
        selected.setSourceRecordPresent(true);
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        service.updateImageMetadata(
                10L, 2L, "  한국관광공사  ", " 한국관광공사 김지호 ",
                " KOGL_TYPE_4 ", " CC BY 4.0 ", " https://example.com/source ");

        verify(destinationMapper).updateImageMetadata(
                2L, "한국관광공사", "한국관광공사 김지호",
                "KOGL_TYPE_4", "CC BY 4.0", "https://example.com/source");
        verify(destinationMapper).upsertImageSourceMetadata(
                2L, "한국관광공사", "한국관광공사 김지호",
                "KOGL_TYPE_4", "CC BY 4.0", "https://example.com/source");
        verify(destinationMapper, never()).clearMainImagesByDestinationId(anyLong());
        verify(destinationMapper, never()).setMainImage(anyLong());
        verify(destinationMapper, never()).updateImageSlide(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(destinationMapper, never()).deleteImageById(anyLong());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void clearingExistingSourceKeepsNullsInTheNewRow() {
        DestinationImage selected = image(2L, 10L, 3, false);
        selected.setSourceRecordPresent(true);
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        service.updateImageMetadata(10L, 2L, null, null, null, null, null);

        verify(destinationMapper).upsertImageSourceMetadata(2L, null, null, null, null, null);
    }

    @Test
    void editingLegacyOnlyImageCopiesUneditedKtoFieldsIntoItsFirstSourceRow() {
        DestinationImage selected = image(2L, 10L, 3, false);
        selected.setExternalContentId("gallery-42");
        selected.setSourceTitle("기존 원본 제목");
        selected.setSourceImageUrl("https://example.com/original.jpg");
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        service.updateImageMetadata(10L, 2L, "새 제공기관", null, "KOGL_TYPE_1",
                null, null);

        ArgumentCaptor<DestinationImage> source = ArgumentCaptor.forClass(DestinationImage.class);
        verify(destinationMapper).insertImageSource(source.capture());
        assertThat(source.getValue())
                .extracting(DestinationImage::getId, DestinationImage::getSourceName,
                        DestinationImage::getExternalContentId, DestinationImage::getSourceTitle,
                        DestinationImage::getSourceImageUrl)
                .containsExactly(2L, "새 제공기관", "gallery-42", "기존 원본 제목",
                        "https://example.com/original.jpg");
        assertThat(invocationsNamed("upsertImageSourceMetadata")).isEmpty();
    }

    @Test
    void bulkSourceFillsBlanksAndPreservesExistingPerPhotoValues() {
        DestinationImage first = image(2L, 10L, 0, false);
        first.setSourceRecordPresent(true);
        first.setPhotographer("개별 촬영자");
        DestinationImage second = image(3L, 10L, 1, false);
        second.setSourceRecordPresent(true);
        when(destinationMapper.findImageById(2L)).thenReturn(first);
        when(destinationMapper.findImageById(3L)).thenReturn(second);

        var result = service.applyBulkSource(10L, List.of(2L, 3L),
                "공공기관", "공통 촬영자", "KOGL_TYPE_1", null,
                "https://example.com/collection", java.util.Set.of(), true, false);

        assertThat(result.appliedImageCount()).isEqualTo(2);
        assertThat(result.skippedFieldCount()).isEqualTo(1);
        assertThat(result.manualReviewImageIds()).containsExactly(2L);
        assertThat(first.getPhotographer()).isEqualTo("개별 촬영자");
        assertThat(first.getCommonSourceUrl()).isEqualTo("https://example.com/collection");
        verify(destinationMapper).updateBulkImageSource(eq(first), any());
        verify(destinationMapper).updateBulkImageSource(eq(second), any());
    }

    @Test
    void bulkSourceRequiresLicenseAndOverwriteConfirmationAndRejectsForeignPhoto() {
        assertThatThrownBy(() -> service.applyBulkSource(10L, List.of(2L),
                null, null, "KOGL_TYPE_1", null, null,
                java.util.Set.of(), false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyBulkSource(10L, List.of(2L),
                "기관", null, null, null, null,
                java.util.Set.of("sourceName"), true, false)).isInstanceOf(IllegalArgumentException.class);
        when(destinationMapper.findImageById(2L)).thenReturn(image(2L, 99L, 0, false));
        assertThatThrownBy(() -> service.applyBulkSource(10L, List.of(2L),
                "기관", null, null, null, null,
                java.util.Set.of(), true, false)).isInstanceOf(IllegalArgumentException.class);
        verify(destinationMapper, never()).updateBulkImageSource(any(), any());
    }

    @Test
    void commonsPhotoSourceCannotBeOverwrittenByManualOrBulkSourceEditing() {
        DestinationImage commons = image(2L, 10L, 0, false);
        commons.setSourceType("WIKIMEDIA_COMMONS");
        commons.setSourceRecordPresent(true);
        when(destinationMapper.findImageById(2L)).thenReturn(commons);

        assertThatThrownBy(() -> service.updateImageMetadataAndPages(10L, 2L, "기관", "촬영자",
                "KOGL_TYPE_1", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Commons");
        assertThatThrownBy(() -> service.applyBulkSource(10L, List.of(2L),
                "기관", null, null, null, null, java.util.Set.of(), false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Commons");
        verify(destinationMapper, never()).updateImageMetadata(any(), any(), any(), any(), any(), any());
        verify(destinationMapper, never()).upsertImageSourceMetadata(any(), any(), any(), any(), any(), any());
        verify(destinationMapper, never()).updateBulkImageSource(any(), any());
        verify(destinationMapper, never()).updateBulkImageLegacy(any(), any());
    }

    @Test
    void bulkSourceOnLegacyOnlyPhotoCopiesUnchangedKtoIdentifiersAndKeepsLegacyUrl() {
        DestinationImage image = image(2L, 10L, 0, false);
        image.setExternalContentId("kto-42");
        image.setSourceUrl("https://example.com/legacy");
        when(destinationMapper.findImageById(2L)).thenReturn(image);

        var result = service.applyBulkSource(10L, List.of(2L),
                "공공기관", null, null, null, "https://example.com/collection",
                java.util.Set.of(), false, false);

        assertThat(result.appliedImageCount()).isEqualTo(1);
        assertThat(image.getExternalContentId()).isEqualTo("kto-42");
        assertThat(image.getSourceUrl()).isEqualTo("https://example.com/legacy");
        verify(destinationMapper).insertImageSource(image);
    }

    @Test
    void editingPhotoPagesKeepsMissingPageParametersAndOtherSourceFields() {
        DestinationImage image = image(2L, 10L, 0, false);
        image.setSourceRecordPresent(true);
        image.setCommonSourceUrl("https://example.com/collection");
        image.setWorkPageUrl("https://example.com/work/2");
        when(destinationMapper.findImageById(2L)).thenReturn(image);

        service.updateImageMetadataAndPages(10L, 2L,
                "공공기관", "개별 촬영자", "KOGL_TYPE_1", null, null,
                null, "https://example.com/work/3");

        verify(destinationMapper).upsertImageSourcePages(
                2L, "https://example.com/collection", "https://example.com/work/3");
    }

    @Test
    void directUploadsGoThroughTheDestinationOnlyImageValidation() {
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new-a.jpg");

        service.saveImages(10L, files("a.jpg"), null, new Integer[0]);

        // 공용 saveFile() 은 더 이상 여행지 업로드에 쓰이지 않는다
        verify(fileUploadService, never()).saveFile(any(), any());
        verify(fileUploadService).saveDestinationImage(any());
    }

    @Test
    void aRejectedFileInTheBatchCleansUpTheAlreadyStoredUploads() {
        when(fileUploadService.saveDestinationImage(any()))
                .thenReturn("/uploads/destinations/new-a.jpg")
                .thenThrow(new IllegalArgumentException(
                        "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다."));

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.saveImages(
                    10L, files("a.jpg", "fake.jpg"), null, new Integer[0]))
                    .isInstanceOf(IllegalArgumentException.class);
            completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);
        });

        // 먼저 저장된 정상 파일도 이번 요청 롤백과 함께 정리된다
        verify(fileUploadService).deleteDestinationFile("/uploads/destinations/new-a.jpg");
    }

    @Test
    void deletingRejectsAnImageThatBelongsToAnotherDestination() {
        DestinationImage otherDestinationImage = image(2001L, 200L, 3, true);
        when(destinationMapper.findImageById(2001L)).thenReturn(otherDestinationImage);

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.deleteImage(100L, 2001L))
                    .isInstanceOf(IllegalArgumentException.class);

            // 커밋되더라도 예약된 파일 정리 자체가 없어야 한다
            commitSynchronizations();
        });

        verify(destinationMapper, never()).deleteImageById(anyLong());
        assertThat(invocationsNamed("updateImageOrder")).isEmpty();
        assertThat(invocationsNamed("setMainImage")).isEmpty();
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
        assertThat(invocationsNamed("findImagesByDestinationId")).isEmpty();
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void deletingAnUnknownImageIsRejectedLikeMainAndSlide() {
        when(destinationMapper.findImageById(404L)).thenReturn(null);

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> service.deleteImage(100L, 404L))
                    .isInstanceOf(IllegalArgumentException.class);
            commitSynchronizations();
        });

        verify(destinationMapper, never()).deleteImageById(anyLong());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void imageCardActionsRejectAnImageFromAnotherDestination() {
        DestinationImage selected = image(2L, 99L, 3, false);
        when(destinationMapper.findImageById(2L)).thenReturn(selected);

        assertThatThrownBy(() -> service.setMainImage(10L, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.toggleSlideImage(10L, 2L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(destinationMapper, never()).clearMainImagesByDestinationId(10L);
        verify(destinationMapper, never()).setMainImage(2L);
        verify(destinationMapper, never()).updateImageSlide(2L, true);
        verify(destinationMapper, never()).updateImageSlide(2L, false);
    }

    private MultipartFile[] files(String... names) {
        byte[] jpeg = jpegBytes();
        return java.util.Arrays.stream(names)
                .map(name -> new MockMultipartFile("files", name, "image/jpeg", jpeg))
                .toArray(MultipartFile[]::new);
    }

    private byte[] jpegBytes() {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(image, "jpg", bytes);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return bytes.toByteArray();
    }

    private DestinationImage image(Long id, Long destinationId, int orderIndex, boolean main) {
        DestinationImage image = new DestinationImage();
        image.setId(id);
        image.setDestinationId(destinationId);
        image.setImageUrl("/uploads/destinations/" + id + ".jpg");
        image.setOrderIndex(orderIndex);
        image.setIsMain(main);
        image.setIsSlide(false);
        return image;
    }

    private List<DestinationImage> insertedImages() {
        ArgumentCaptor<DestinationImage> captor = ArgumentCaptor.forClass(DestinationImage.class);
        verify(destinationMapper, org.mockito.Mockito.atLeastOnce()).insertImage(captor.capture());
        return captor.getAllValues();
    }

    private List<Invocation> invocationsNamed(String methodName) {
        return mockingDetails(destinationMapper).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                .toList();
    }

    private List<List<Object>> orderUpdates() {
        return invocationsNamed("updateImageOrder").stream()
                .map(invocation -> List.of(invocation.getArgument(0), invocation.getArgument(1)))
                .toList();
    }

    private void withTransactionSynchronization(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void completeSynchronizations(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private void commitSynchronizations() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
