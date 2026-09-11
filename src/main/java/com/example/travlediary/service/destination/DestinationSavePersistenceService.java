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
        registerDestination(form, userId, null, preparedPhotos);
    }

    /**
     * TourAPI 여행지 저장. 후보 조회 때 이미 한 번 걸렀더라도 같은 트랜잭션 안에서 한 번 더 확인해,
     * 재클릭이나 동시 작업으로 같은 contentId 가 두 번 저장되지 않게 한다.
     *
     * @param externalContentId null 이면 관리자 직접 등록과 똑같이 저장한다.
     * @return 저장된 여행지 번호
     */
    @Transactional
    public Long registerDestination(DestinationForm form,
                                    Long userId,
                                    String externalContentId,
                                    List<PreparedKtoPhoto> preparedPhotos) {
        if (externalContentId != null
                && destinationService.existsTourApiDestination(externalContentId)) {
            throw new DuplicateTourApiDestinationException(externalContentId);
        }
        Long destinationId = destinationService.registerDestination(form, userId, externalContentId);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
        return destinationId;
    }

    @Transactional
    public void updateDestination(Long destinationId,
                                  DestinationForm form,
                                  List<PreparedKtoPhoto> preparedPhotos) {
        destinationService.updateDestination(destinationId, form);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
    }
}
