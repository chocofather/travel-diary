package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.dto.kto.KtoTourImageCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KtoPhotoImportService {

    private final KtoPhotoDownloadService downloadService;
    private final KtoTourService ktoTourService;
    private final Clock clock;

    @Autowired
    public KtoPhotoImportService(KtoPhotoDownloadService downloadService, KtoTourService ktoTourService) {
        this(downloadService, ktoTourService, Clock.systemDefaultZone());
    }

    KtoPhotoImportService(KtoPhotoDownloadService downloadService, KtoTourService ktoTourService, Clock clock) {
        this.downloadService = downloadService;
        this.ktoTourService = ktoTourService;
        this.clock = clock;
    }

    public List<PreparedKtoPhoto> preparePhotos(List<KtoSelectedPhotoRequest> selectedPhotos) {
        if (selectedPhotos == null || selectedPhotos.isEmpty()) {
            return List.of();
        }

        Timestamp licenseCheckedAt = Timestamp.from(clock.instant());
        List<PreparedKtoPhoto> preparedPhotos = new ArrayList<>(selectedPhotos.size());
        Map<String, List<KtoTourImageCandidate>> tourCandidatesByContentId = new HashMap<>();
        try {
            for (KtoSelectedPhotoRequest selectedPhoto : selectedPhotos) {
                LicensedPhoto licensed = licensedPhoto(selectedPhoto, tourCandidatesByContentId);
                KtoDownloadedPhoto downloadedPhoto = downloadService.download(licensed.imageUrl());
                preparedPhotos.add(new PreparedKtoPhoto(
                        downloadedPhoto.localImageUrl(),
                        downloadedPhoto.sourceImageUrl(),
                        selectedPhoto.externalContentId(),
                        selectedPhoto.title(),
                        selectedPhoto.photographer(),
                        selectedPhoto.isMain(),
                        new Timestamp(licenseCheckedAt.getTime()),
                        licensed.sourceType(),
                        licensed.licenseType()));
            }
            return List.copyOf(preparedPhotos);
        } catch (InvalidKtoPhotoUrlException | KtoPhotoDownloadException exception) {
            cleanupPreparedPhotos(preparedPhotos);
            throw exception;
        } catch (RuntimeException exception) {
            cleanupPreparedPhotos(preparedPhotos);
            throw new KtoPhotoImportException();
        }
    }

    /**
     * 선택한 사진의 출처와 라이선스를 서버에서 정한다. 화면은 출처·라이선스 값을 보내지 않는다.
     *
     * <ul>
     *   <li>관광사진 API 이미지 주소: 공식 API 데이터셋 이용허락범위를 근거로 TYPE1 처리한다.</li>
     *   <li>그 밖의 주소(TourAPI): 해당 콘텐츠 이미지를 다시 조회해 같은 원본 이미지의
     *       사진별 저작권 구분 코드로 판정한다. 목록에 없거나 1·3유형이 아니면 저장하지 않는다.</li>
     * </ul>
     */
    private LicensedPhoto licensedPhoto(KtoSelectedPhotoRequest selectedPhoto,
                                        Map<String, List<KtoTourImageCandidate>> tourCandidatesByContentId) {
        if (KtoPhotoGalleryService.isGalleryImageUrl(selectedPhoto.imageUrl())) {
            return new LicensedPhoto(selectedPhoto.imageUrl(),
                    KtoPhotoGalleryService.SOURCE_TYPE, KtoPhotoGalleryService.DATASET_LICENSE_TYPE);
        }

        String contentId = selectedPhoto.externalContentId().strip();
        List<KtoTourImageCandidate> candidates = tourCandidatesByContentId.computeIfAbsent(
                contentId, ktoTourService::getImportableImages);
        String selectedImageKey = KtoPhotoSearchService.imageKey(selectedPhoto.imageUrl());
        KtoTourImageCandidate candidate = candidates.stream()
                .filter(image -> KtoPhotoSearchService.imageKey(image.imageUrl()).equals(selectedImageKey))
                .findFirst()
                .orElseThrow(KtoPhotoImportException::new);
        KtoFestivalImageLicense license = KtoFestivalImageLicense
                .fromCopyrightDivisionCode(candidate.copyrightDivisionCode())
                .orElseThrow(KtoPhotoImportException::new);
        return new LicensedPhoto(candidate.imageUrl(), KtoPhotoSearchService.TOUR_SOURCE_TYPE, license.name());
    }

    private record LicensedPhoto(String imageUrl, String sourceType, String licenseType) {
    }

    public void cleanupPreparedPhotos(List<PreparedKtoPhoto> preparedPhotos) {
        if (preparedPhotos == null || preparedPhotos.isEmpty()) {
            return;
        }
        for (PreparedKtoPhoto preparedPhoto : preparedPhotos) {
            if (preparedPhoto == null) {
                continue;
            }
            try {
                downloadService.deleteDownloadedPhoto(preparedPhoto.localImageUrl());
            } catch (RuntimeException cleanupFailure) {
                log.warn("KTO 관광사진 import 실패 파일을 정리하지 못했습니다. (원인: {})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }
}
