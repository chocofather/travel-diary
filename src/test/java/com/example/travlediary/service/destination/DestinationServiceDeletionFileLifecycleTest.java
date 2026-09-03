package com.example.travlediary.service.destination;

import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.comment.DestinationCommentImageMapper;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.course.CourseService;
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
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
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
    private DestinationCommentMapper destinationCommentMapper;
    @Mock
    private DestinationCommentImageMapper destinationCommentImageMapper;
    @Mock
    private UserMapper userMapper;
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
    /** 여행지가 빠진 코스의 STOP 번호를 다시 매기는 일만 맡긴다. */
    @Mock
    private CourseService courseService;

    @TempDir
    Path uploadRoot;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        FileUploadService fileUploadService = new FileUploadService(uploadRoot.toString());
        DestinationImageService destinationImageService =
                new DestinationImageService(destinationMapper, fileUploadService);
        ReflectionTestUtils.setField(destinationImageService, "uploadDir", uploadRoot.toString());
        DestinationCommentService destinationCommentService = new DestinationCommentService(
                destinationMapper,
                destinationCommentMapper,
                destinationCommentImageMapper,
                userMapper,
                fileUploadService);
        ReflectionTestUtils.setField(destinationCommentService, "uploadPath", uploadRoot.toString());
        destinationService = new DestinationService(
                destinationMapper,
                destinationImageService,
                bookmarkMapper,
                amenityService,
                destinationCommentService,
                courseService,
                accommodationInfoService,
                attractionInfoService,
                restaurantInfoService,
                activityInfoService,
                shopInfoService,
                new DestinationLocalizationService(destinationMapper)
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

    @Test
    void deletingDestinationAlsoRemovesItsBookmarksBeforeTheDestinationRow() {
        withTransactionSynchronization(() -> destinationService.deleteById(9L));

        InOrder deletionOrder = inOrder(bookmarkMapper, destinationMapper);
        deletionOrder.verify(bookmarkMapper)
                .deleteByTarget(BookmarkTargetType.DESTINATION.name(), 9L);
        deletionOrder.verify(destinationMapper).deleteById(9L);
    }

    @Test
    void bookmarkDeletionSharesTheDestinationDeletionTransaction() throws Exception {
        Method deleteById = DestinationService.class.getMethod("deleteById", Long.class);
        Transactional transactional = deleteById.getAnnotation(Transactional.class);

        // 별도 transaction 없이 기존 삭제 transaction 안에서 함께 rollback 된다
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);

        doThrow(new IllegalStateException("db failure")).when(destinationMapper).deleteById(9L);
        withTransactionSynchronization(() -> {
            assertThatThrownBy(() -> destinationService.deleteById(9L))
                    .isInstanceOf(IllegalStateException.class);
            rollbackSynchronizations();
        });

        verify(bookmarkMapper).deleteByTarget(BookmarkTargetType.DESTINATION.name(), 9L);
    }

    @Test
    void everyCommentImageFileIsDeletedOnlyAfterCommit() throws Exception {
        Path first = managedCommentImage("first.jpg");
        Path second = managedCommentImage("second.jpg");
        Path fromDeletedComment = managedCommentImage("soft-deleted.jpg");
        when(destinationCommentImageMapper.findAllImageUrlsByDestinationId(9L)).thenReturn(List.of(
                "/uploads/comments/first.jpg",
                "/uploads/comments/second.jpg",
                "/uploads/comments/soft-deleted.jpg"));

        withTransactionSynchronization(() -> {
            destinationService.deleteById(9L);

            assertThat(first).exists();
            assertThat(second).exists();
            assertThat(fromDeletedComment).exists();
            commitSynchronizations();
        });

        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
        assertThat(fromDeletedComment).doesNotExist();
    }

    @Test
    void commentImageUrlsAreCollectedBeforeTheCommentRowsAreDeleted() {
        withTransactionSynchronization(() -> destinationService.deleteById(9L));

        InOrder collectionOrder = inOrder(destinationCommentImageMapper, destinationMapper);
        collectionOrder.verify(destinationCommentImageMapper).findAllImageUrlsByDestinationId(9L);
        collectionOrder.verify(destinationMapper).deleteCommentsByDestinationId(9L);
    }

    @Test
    void commentImageFilesSurviveWhenTheDeletionRollsBack() throws Exception {
        Path first = managedCommentImage("first.jpg");
        Path second = managedCommentImage("second.jpg");
        when(destinationCommentImageMapper.findAllImageUrlsByDestinationId(9L)).thenReturn(List.of(
                "/uploads/comments/first.jpg",
                "/uploads/comments/second.jpg"));
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

    @Test
    void oneFailedCommentImageCleanupDoesNotStopTheRemainingFiles() throws Exception {
        Path outside = Files.write(uploadRoot.resolve("outside.jpg"), new byte[]{1, 2, 3});
        Path second = managedCommentImage("second.jpg");
        Path third = managedCommentImage("third.jpg");
        when(destinationCommentImageMapper.findAllImageUrlsByDestinationId(9L)).thenReturn(List.of(
                "/uploads/comments/../outside.jpg",
                "/uploads/comments/second.jpg",
                "/uploads/comments/third.jpg"));

        withTransactionSynchronization(() -> {
            destinationService.deleteById(9L);
            commitSynchronizations();
        });

        assertThat(outside).exists();
        assertThat(second).doesNotExist();
        assertThat(third).doesNotExist();
    }

    @Test
    void cleanupIgnoresUrlsOutsideTheManagedCommentDirectory() throws Exception {
        Path destinationImage = managedImage("keep.jpg");
        when(destinationCommentImageMapper.findAllImageUrlsByDestinationId(9L)).thenReturn(List.of(
                "/uploads/destinations/keep.jpg",
                "https://cdn.example.com/comments/remote.jpg"));

        withTransactionSynchronization(() -> {
            destinationService.deleteById(9L);
            commitSynchronizations();
        });

        assertThat(destinationImage).exists();
    }

    private Path managedCommentImage(String fileName) throws Exception {
        Path comments = Files.createDirectories(uploadRoot.resolve("comments"));
        return Files.write(comments.resolve(fileName), new byte[]{1, 2, 3});
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
