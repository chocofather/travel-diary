package com.example.travlediary.service.destination;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationServiceDeletionFileLifecycleTest {

    @Mock
    private DestinationMapper destinationMapper;
    @Mock
    private BookmarkMapper bookmarkMapper;
    @Mock
    private AmenityService amenityService;
    @Mock
    private DestinationCommentService destinationCommentService;
    @Mock
    private AccommodationInfoService accommodationInfoService;
    @Mock
    private AttractionInfoService attractionInfoService;
    @Mock
    private RestaurantInfoService restaurantInfoService;
    @Mock
    private ActivityInfoService activityInfoService;
    @Mock
    private ShopInfoService shopInfoService;

    @TempDir
    Path uploadRoot;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        FileUploadService fileUploadService = new FileUploadService(uploadRoot.toString());
        DestinationImageService destinationImageService =
                new DestinationImageService(destinationMapper, fileUploadService);
        ReflectionTestUtils.setField(destinationImageService, "uploadDir", uploadRoot.toString());
        destinationService = new DestinationService(
                destinationMapper,
                destinationImageService,
                bookmarkMapper,
                amenityService,
                destinationCommentService,
                accommodationInfoService,
                attractionInfoService,
                restaurantInfoService,
                activityInfoService,
                shopInfoService
        );
        ReflectionTestUtils.setField(destinationService, "uploadPath", uploadRoot.toString());
    }

    @Test
    void destinationImageFilesAreDeletedOnlyAfterCommit() throws Exception {
        Path imageFile = managedImage("first.jpg");
        when(destinationMapper.findImagesByDestinationId(9L))
                .thenReturn(List.of(image("/uploads/destinations/first.jpg")));

        withTransactionSynchronization(() -> {
            destinationService.deleteById(9L);

            assertThat(imageFile).exists();
            commitSynchronizations();
        });

        assertThat(imageFile).doesNotExist();
    }

    @Test
    void destinationDatabaseFailureKeepsEveryImageFileOnRollback() throws Exception {
        Path first = managedImage("first.jpg");
        Path second = managedImage("second.jpg");
        when(destinationMapper.findImagesByDestinationId(9L)).thenReturn(List.of(
                image("/uploads/destinations/first.jpg"),
                image("/uploads/destinations/second.jpg")
        ));
        doThrow(new IllegalStateException("db failure"))
                .when(destinationMapper).deleteById(9L);

        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> destinationService.deleteById(9L))
                    .isInstanceOf(IllegalStateException.class);
            rollbackSynchronizations();
        });

        assertThat(first).exists();
        assertThat(second).exists();
    }

    private Path managedImage(String fileName) throws Exception {
        Path destinations = Files.createDirectories(uploadRoot.resolve("destinations"));
        return Files.write(destinations.resolve(fileName), new byte[]{1, 2, 3});
    }

    private DestinationImage image(String imageUrl) {
        DestinationImage image = new DestinationImage();
        image.setImageUrl(imageUrl);
        return image;
    }

    private void withTransactionSynchronization(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void commitSynchronizations() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollbackSynchronizations() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
