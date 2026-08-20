package com.example.travlediary.service.kto;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KtoPhotoImportPersistenceService {

    private static final String SOURCE_TYPE = "KTO_PHOTO_GALLERY";
    private static final String SOURCE_NAME = "한국관광공사";
    private static final String LICENSE_TYPE = "KOGL_TYPE_1";

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
        image.setSourceType(SOURCE_TYPE);
        image.setSourceName(SOURCE_NAME);
        image.setExternalContentId(preparedPhoto.externalContentId());
        image.setSourceTitle(preparedPhoto.title());
        image.setPhotographer(preparedPhoto.photographer());
        image.setLicenseType(LICENSE_TYPE);
        image.setSourceImageUrl(preparedPhoto.sourceImageUrl());
        image.setLicenseCheckedAt(preparedPhoto.licenseCheckedAt());
        image.setIsMain(preparedPhoto.isMain());
        image.setIsSlide(false);
        return image;
    }
}
