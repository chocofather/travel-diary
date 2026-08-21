package com.example.travlediary.service.destination;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.file.FileUploadService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
