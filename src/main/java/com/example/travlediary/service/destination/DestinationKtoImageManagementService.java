package com.example.travlediary.service.destination;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.service.kto.KtoPhotoImportPersistenceService;
import com.example.travlediary.service.kto.KtoPhotoImportService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationKtoImageManagementService {

    private final KtoPhotoImportService ktoPhotoImportService;
    private final KtoPhotoImportPersistenceService persistenceService;

    public void addPhotos(Long destinationId, List<KtoSelectedPhotoRequest> selectedPhotos) {
        if (selectedPhotos == null || selectedPhotos.isEmpty()) {
            return;
        }

        List<PreparedKtoPhoto> preparedPhotos = ktoPhotoImportService.preparePhotos(selectedPhotos);
        try {
            persistenceService.persistPhotos(destinationId, preparedPhotos);
        } catch (RuntimeException exception) {
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            throw exception;
        }
    }
}
