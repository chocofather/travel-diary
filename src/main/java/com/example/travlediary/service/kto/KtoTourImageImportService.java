package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourImageCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * TourAPI 대표/추가 이미지를 여행지 이미지로 내려받는다.
 * 저장 위치와 파일 검증은 단건 등록과 같은 {@link KtoPhotoDownloadService} 를 쓰고,
 * DB 저장은 {@link KtoPhotoImportPersistenceService} 가 그대로 맡는다.
 * 저작권 코드를 확인할 수 없거나 내려받지 못한 이미지는 건너뛰고 여행지 등록 자체는 진행한다.
 */
@Slf4j
@Service
public class KtoTourImageImportService {

    private static final String SOURCE_TYPE = "KTO_TOURAPI";

    private final KtoTourService ktoTourService;
    private final KtoPhotoDownloadService downloadService;
    private final Clock clock;

    @Autowired
    public KtoTourImageImportService(KtoTourService ktoTourService,
                                     KtoPhotoDownloadService downloadService) {
        this(ktoTourService, downloadService, Clock.systemDefaultZone());
    }

    KtoTourImageImportService(KtoTourService ktoTourService,
                              KtoPhotoDownloadService downloadService,
                              Clock clock) {
        this.ktoTourService = ktoTourService;
        this.downloadService = downloadService;
        this.clock = clock;
    }

    public List<PreparedKtoPhoto> preparePhotos(String contentId, String fallbackTitle) {
        List<KtoTourImageCandidate> candidates;
        try {
            candidates = ktoTourService.getImportableImages(contentId);
        } catch (KtoTourApiException exception) {
            log.warn("TourAPI 이미지를 조회하지 못해 이미지 없이 등록합니다. (contentId={})", contentId);
            return List.of();
        }

        Timestamp licenseCheckedAt = Timestamp.from(clock.instant());
        List<PreparedKtoPhoto> preparedPhotos = new ArrayList<>();
        boolean mainAssigned = false;
        for (KtoTourImageCandidate candidate : candidates) {
            KtoFestivalImageLicense license = KtoFestivalImageLicense
                    .fromCopyrightDivisionCode(candidate.copyrightDivisionCode())
                    .orElse(null);
            if (license == null) {
                continue;
            }
            try {
                KtoDownloadedPhoto downloaded = downloadService.download(candidate.imageUrl());
                // 대표이미지를 못 받았으면 첫 성공 이미지가 대표가 된다.
                boolean isMain = !mainAssigned;
                mainAssigned = true;
                preparedPhotos.add(new PreparedKtoPhoto(
                        downloaded.localImageUrl(),
                        downloaded.sourceImageUrl(),
                        candidate.contentId(),
                        firstNonBlank(candidate.imageName(), fallbackTitle),
                        null,
                        isMain,
                        new Timestamp(licenseCheckedAt.getTime()),
                        SOURCE_TYPE,
                        license.name()));
            } catch (InvalidKtoPhotoUrlException | KtoPhotoDownloadException exception) {
                log.warn("TourAPI 이미지를 저장하지 못해 건너뜁니다. (contentId={})", candidate.contentId());
            }
        }
        return List.copyOf(preparedPhotos);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
