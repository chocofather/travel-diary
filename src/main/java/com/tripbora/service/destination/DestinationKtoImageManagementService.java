package com.tripbora.service.destination;

import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.service.kto.KtoPhotoImportPersistenceService;
import com.tripbora.service.kto.KtoPhotoImportService;
import com.tripbora.service.kto.PreparedKtoPhoto;
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
