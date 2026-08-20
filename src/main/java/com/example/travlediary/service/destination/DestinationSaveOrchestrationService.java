package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoPhotoImportService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationSaveOrchestrationService {

    private final KtoPhotoImportService ktoPhotoImportService;
    private final DestinationSavePersistenceService persistenceService;

    public void registerDestination(DestinationForm form,
                                    Long userId,
                                    List<KtoSelectedPhotoRequest> selectedPhotos) {
        List<KtoSelectedPhotoRequest> selections = safeSelections(selectedPhotos);
        validateCreateMainSelection(form, selections);

        List<PreparedKtoPhoto> preparedPhotos = preparePhotos(selections);
        try {
            persistenceService.registerDestination(form, userId, preparedPhotos);
        } catch (RuntimeException exception) {
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            throw exception;
        }
    }

    public void updateDestination(Long destinationId,
                                  DestinationForm form,
                                  List<KtoSelectedPhotoRequest> selectedPhotos) {
        List<PreparedKtoPhoto> preparedPhotos = preparePhotos(safeSelections(selectedPhotos));
        try {
            persistenceService.updateDestination(destinationId, form, preparedPhotos);
        } catch (RuntimeException exception) {
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            throw exception;
        }
    }

    private List<PreparedKtoPhoto> preparePhotos(List<KtoSelectedPhotoRequest> selections) {
        return selections.isEmpty()
                ? List.of()
                : ktoPhotoImportService.preparePhotos(selections);
    }

    private List<KtoSelectedPhotoRequest> safeSelections(
            List<KtoSelectedPhotoRequest> selectedPhotos) {
        return selectedPhotos == null ? List.of() : selectedPhotos;
    }

    private void validateCreateMainSelection(DestinationForm form,
                                             List<KtoSelectedPhotoRequest> selectedPhotos) {
        if (!form.isMain() || !hasDirectUpload(form.getImages())) {
            return;
        }
        if (selectedPhotos.stream().anyMatch(KtoSelectedPhotoRequest::isMain)) {
            throw new InvalidKtoSelectedPhotosException();
        }
    }

    private boolean hasDirectUpload(MultipartFile[] images) {
        if (images == null) {
            return false;
        }
        for (MultipartFile image : images) {
            if (image != null && !image.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
