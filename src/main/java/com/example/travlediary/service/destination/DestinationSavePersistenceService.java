package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.service.kto.KtoPhotoImportPersistenceService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationSavePersistenceService {

    private final DestinationService destinationService;
    private final KtoPhotoImportPersistenceService ktoPhotoImportPersistenceService;

    @Transactional
    public void registerDestination(DestinationForm form,
                                    Long userId,
                                    List<PreparedKtoPhoto> preparedPhotos) {
        Long destinationId = destinationService.registerDestination(form, userId);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
    }

    @Transactional
    public void updateDestination(Long destinationId,
                                  DestinationForm form,
                                  List<PreparedKtoPhoto> preparedPhotos) {
        destinationService.updateDestination(destinationId, form);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
    }
}
