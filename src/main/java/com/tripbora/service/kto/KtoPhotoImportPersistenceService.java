package com.tripbora.service.kto;

import com.tripbora.model.DestinationImage;
import com.tripbora.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KtoPhotoImportPersistenceService {

    /**
     * 출처를 따로 넘기지 않는 관광사진 갤러리 경로의 기본값.
     * 라이선스는 공식 API 데이터셋 이용허락범위를 근거로 한 TYPE1 이다({@link KtoPhotoGalleryService#DATASET_LICENSE_TYPE}).
     */
    private static final String SOURCE_TYPE = KtoPhotoGalleryService.SOURCE_TYPE;
    private static final String SOURCE_NAME = KtoPhotoGalleryService.SOURCE_NAME;
    private static final String LICENSE_TYPE = KtoPhotoGalleryService.DATASET_LICENSE_TYPE;

    private final DestinationImageService destinationImageService;

    @Transactional
    public void persistPhotos(Long destinationId, List<PreparedKtoPhoto> preparedPhotos) {
        if (preparedPhotos == null || preparedPhotos.isEmpty()) {
            return;
        }

        List<DestinationImage> images = preparedPhotos.stream()
                .map(preparedPhoto -> destinationImage(destinationId, preparedPhoto))
                .toList();
        destinationImageService.saveImages(destinationId, images);
    }

    private DestinationImage destinationImage(Long destinationId, PreparedKtoPhoto preparedPhoto) {
        DestinationImage image = new DestinationImage();
        image.setDestinationId(destinationId);
        image.setImageUrl(preparedPhoto.localImageUrl());
        image.setSourceType(valueOrDefault(preparedPhoto.sourceType(), SOURCE_TYPE));
        image.setSourceName(SOURCE_NAME);
        image.setExternalContentId(preparedPhoto.externalContentId());
        image.setSourceTitle(preparedPhoto.title());
        image.setPhotographer(preparedPhoto.photographer());
        image.setLicenseType(valueOrDefault(preparedPhoto.licenseType(), LICENSE_TYPE));
        image.setSourceImageUrl(preparedPhoto.sourceImageUrl());
        image.setLicenseCheckedAt(preparedPhoto.licenseCheckedAt());
        image.setIsMain(preparedPhoto.isMain());
        image.setIsSlide(false);
        return image;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
