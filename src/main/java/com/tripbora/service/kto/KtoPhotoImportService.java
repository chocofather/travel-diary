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
        for (int index = 0; index < selectedPhotos.size(); index++) {
            KtoSelectedPhotoRequest selectedPhoto = selectedPhotos.get(index);
            KtoPhotoItemFailureException.Stage stage = KtoPhotoItemFailureException.Stage.LICENSE;
            try {
                LicensedPhoto licensed = licensedPhoto(selectedPhoto, tourCandidatesByContentId);
                stage = KtoPhotoItemFailureException.Stage.DOWNLOAD;
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
            } catch (RuntimeException exception) {
                // 한 장이라도 실패하면 지금처럼 앞서 받은 파일까지 지우고 전체를 저장하지 않는다
                cleanupPreparedPhotos(preparedPhotos);
                throw itemFailure(index + 1, selectedPhoto, stage, exception);
            }
        }
        return List.copyOf(preparedPhotos);
    }

    private KtoPhotoItemFailureException itemFailure(int position,
                                                     KtoSelectedPhotoRequest selectedPhoto,
                                                     KtoPhotoItemFailureException.Stage stage,
                                                     RuntimeException exception) {
        KtoPhotoItemFailureException.Stage failedStage = exception instanceof InvalidKtoPhotoUrlException
                ? KtoPhotoItemFailureException.Stage.VALIDATION
                : stage;
        String reason = failureReason(exception);
        KtoPhotoItemFailureException failure = new KtoPhotoItemFailureException(
                position, selectedPhoto.externalContentId(), selectedPhoto.imageUrl(), selectedPhoto.title(),
                failedStage, reason, exception);
        String logMessage = "KTO 관광사진 준비 실패: {}번째 사진, 단계={}, externalContentId={}, imageUrl={}, "
                + "title={}, 예외={}, 사유={}";
        if (isKnownFailure(exception)) {
            log.warn(logMessage, position, failedStage.code(), failure.externalContentId(), failure.imageUrl(),
                    failure.title(), failure.causeType(), reason);
        } else {
            // 예상하지 못한 예외는 원인을 추적할 수 있게 stack trace 를 함께 남긴다
            log.warn(logMessage, position, failedStage.code(), failure.externalContentId(), failure.imageUrl(),
                    failure.title(), failure.causeType(), reason, exception);
        }
        return failure;
    }

    private boolean isKnownFailure(RuntimeException exception) {
        return exception instanceof InvalidKtoPhotoUrlException
                || exception instanceof KtoPhotoDownloadException
                || exception instanceof KtoPhotoImportException
                || exception instanceof KtoTourApiException;
    }

    // 사유에는 서버가 정한 짧은 설명만 쓴다. 외부 API 예외 메시지는 요청 주소가 섞일 수 있어 넣지 않는다
    private String failureReason(RuntimeException exception) {
        if (exception instanceof InvalidKtoPhotoUrlException) {
            return "허용되지 않은 관광사진 이미지 주소";
        }
        if (exception instanceof KtoPhotoDownloadException downloadException) {
            if (exception instanceof PhotoDownloadRateLimitedException) {
                return "원본 서버 요청 제한(HTTP 429/503)";
            }
            return downloadException.reason() == null ? "다운로드 실패" : downloadException.reason();
        }
        if (exception instanceof KtoPhotoImportException importException && importException.reason() != null) {
            return importException.reason();
        }
        if (exception instanceof KtoTourApiException tourApiException) {
            return "TourAPI 이미지 재조회 실패(" + tourApiException.getKind() + ")";
        }
        return "예상하지 못한 오류";
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
                .orElseThrow(() -> new KtoPhotoImportException("TourAPI 재조회 결과에서 같은 이미지를 찾지 못함"));
        KtoFestivalImageLicense license = KtoFestivalImageLicense
                .fromCopyrightDivisionCode(candidate.copyrightDivisionCode())
                .orElseThrow(() -> new KtoPhotoImportException(
                        "저작권 구분 코드로 이용 가능 유형을 판정할 수 없음(cpyrhtDivCd="
                                + candidate.copyrightDivisionCode() + ")"));
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
