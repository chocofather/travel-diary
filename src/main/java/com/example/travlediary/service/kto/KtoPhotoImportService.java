package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KtoPhotoImportService {

    private final KtoPhotoDownloadService downloadService;
    private final Clock clock;

    @Autowired
    public KtoPhotoImportService(KtoPhotoDownloadService downloadService) {
        this(downloadService, Clock.systemDefaultZone());
    }

    KtoPhotoImportService(KtoPhotoDownloadService downloadService, Clock clock) {
        this.downloadService = downloadService;
        this.clock = clock;
    }

    public List<PreparedKtoPhoto> preparePhotos(List<KtoSelectedPhotoRequest> selectedPhotos) {
        if (selectedPhotos == null || selectedPhotos.isEmpty()) {
            return List.of();
        }

        Timestamp licenseCheckedAt = Timestamp.from(clock.instant());
        List<PreparedKtoPhoto> preparedPhotos = new ArrayList<>(selectedPhotos.size());
        try {
            for (KtoSelectedPhotoRequest selectedPhoto : selectedPhotos) {
                KtoDownloadedPhoto downloadedPhoto = downloadService.download(selectedPhoto.imageUrl());
                preparedPhotos.add(new PreparedKtoPhoto(
                        downloadedPhoto.localImageUrl(),
                        downloadedPhoto.sourceImageUrl(),
                        selectedPhoto.externalContentId(),
                        selectedPhoto.title(),
                        selectedPhoto.photographer(),
                        selectedPhoto.isMain(),
                        new Timestamp(licenseCheckedAt.getTime())));
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
