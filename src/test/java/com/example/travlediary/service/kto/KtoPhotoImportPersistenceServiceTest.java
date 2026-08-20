package com.example.travlediary.service.kto;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.service.destination.DestinationImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KtoPhotoImportPersistenceServiceTest {

    @Mock private DestinationImageService destinationImageService;
    @Captor private ArgumentCaptor<List<DestinationImage>> imagesCaptor;

    @Test
    void persistsPreparedPhotosThroughDestinationImageServiceWithServerMetadata() {
        KtoPhotoImportPersistenceService service =
                new KtoPhotoImportPersistenceService(destinationImageService);
        Timestamp checkedAt = Timestamp.from(Instant.parse("2026-08-21T03:04:05Z"));
        PreparedKtoPhoto first = new PreparedKtoPhoto(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                "https://tong.visitkorea.or.kr/cms2/website/10/validated-a.jpg",
                "content-a", "경복궁 봄", "촬영자 A", false, checkedAt);
        PreparedKtoPhoto second = new PreparedKtoPhoto(
                "/uploads/destinations/22222222-2222-4222-8222-222222222222.png",
                "https://tong.visitkorea.or.kr/cms2/website/20/validated-b.png",
                "content-b", "경복궁 야경", "촬영자 B", true, checkedAt);

        service.persistPhotos(10L, List.of(first, second));

        verify(destinationImageService).saveImages(eq(10L), imagesCaptor.capture());
        assertThat(imagesCaptor.getValue())
                .extracting(
                        DestinationImage::getDestinationId,
                        DestinationImage::getImageUrl,
                        DestinationImage::getSourceImageUrl,
                        DestinationImage::getExternalContentId,
                        DestinationImage::getSourceTitle,
                        DestinationImage::getPhotographer,
                        DestinationImage::getIsMain)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                10L, first.localImageUrl(), first.sourceImageUrl(),
                                "content-a", "경복궁 봄", "촬영자 A", false),
                        org.assertj.core.groups.Tuple.tuple(
                                10L, second.localImageUrl(), second.sourceImageUrl(),
                                "content-b", "경복궁 야경", "촬영자 B", true));
        assertThat(imagesCaptor.getValue())
                .allSatisfy(image -> {
                    assertThat(image.getSourceType()).isEqualTo("KTO_PHOTO_GALLERY");
                    assertThat(image.getSourceName()).isEqualTo("한국관광공사");
                    assertThat(image.getLicenseType()).isEqualTo("KOGL_TYPE_1");
                    assertThat(image.getIsSlide()).isFalse();
                    assertThat(image.getOrderIndex()).isNull();
                });
    }

    @Test
    void emptyPreparedPhotosDoNotCallDestinationImageService() {
        KtoPhotoImportPersistenceService service =
                new KtoPhotoImportPersistenceService(destinationImageService);

        service.persistPhotos(10L, List.of());
        service.persistPhotos(10L, null);

        verify(destinationImageService, never()).saveImages(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void persistenceBoundaryIsTransactional() throws Exception {
        Method method = KtoPhotoImportPersistenceService.class
                .getMethod("persistPhotos", Long.class, List.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }
}
