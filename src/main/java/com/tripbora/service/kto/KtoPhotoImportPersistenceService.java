package com.tripbora.service.kto;

import com.tripbora.model.DestinationImage;
import com.tripbora.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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

        // 한 장씩 넘겨도 같은 트랜잭션이라 순서·대표 처리는 같고, 실패하면 전체가 롤백된다. 실패한 사진만 식별한다
        for (int index = 0; index < preparedPhotos.size(); index++) {
            PreparedKtoPhoto preparedPhoto = preparedPhotos.get(index);
            try {
                destinationImageService.saveImages(destinationId,
                        List.of(destinationImage(destinationId, preparedPhoto)));
            } catch (RuntimeException exception) {
                throw persistFailure(destinationId, index + 1, preparedPhoto, exception);
            }
        }
    }

    private KtoPhotoItemFailureException persistFailure(Long destinationId, int position,
                                                        PreparedKtoPhoto preparedPhoto,
                                                        RuntimeException exception) {
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(exception);
        KtoPhotoItemFailureException failure = new KtoPhotoItemFailureException(
                position, preparedPhoto.externalContentId(), preparedPhoto.sourceImageUrl(), preparedPhoto.title(),
                KtoPhotoItemFailureException.Stage.PERSIST,
                "DB 저장 실패(" + rootCause.getClass().getSimpleName() + ")", exception);
        // DB 원인 메시지(예: 컬럼 길이·문자셋 오류)는 로그에만 남긴다
        log.warn("KTO 관광사진 저장 실패: destinationId={}, {}번째 사진, 단계={}, externalContentId={}, imageUrl={}, "
                        + "title={}, photographerLength={}, 예외={}, 원인={}: {}",
                destinationId, position, failure.stage().code(), failure.externalContentId(), failure.imageUrl(),
                failure.title(), preparedPhoto.photographer() == null ? 0 : preparedPhoto.photographer().length(),
                exception.getClass().getSimpleName(), rootCause.getClass().getSimpleName(), rootCause.getMessage());
        return failure;
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
