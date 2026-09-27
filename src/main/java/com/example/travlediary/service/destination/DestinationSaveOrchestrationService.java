package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.service.kto.InvalidKtoSelectedPhotosException;
import com.example.travlediary.service.kto.KtoPhotoImportService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import com.example.travlediary.service.wikidata.CommonsPhotoImportService;
import com.example.travlediary.service.wikidata.PreparedCommonsPhoto;
import com.example.travlediary.service.wikidata.WikidataRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationSaveOrchestrationService {

    private final KtoPhotoImportService ktoPhotoImportService;
    private final DestinationSavePersistenceService persistenceService;
    @Autowired private WikidataRegistrationService wikidataRegistrationService;
    @Autowired private CommonsPhotoImportService commonsPhotoImportService;

    public void registerDestination(DestinationForm form,
                                    Long userId,
                                    List<KtoSelectedPhotoRequest> selectedPhotos) {
        List<KtoSelectedPhotoRequest> selections = safeSelections(selectedPhotos);
        validateCreateMainSelection(form, selections);

        boolean wikidataRequested = form.getWikidataQid() != null && !form.getWikidataQid().isBlank();
        boolean wikipediaRequested = form.getWikipediaRevisionIds() != null
                && form.getWikipediaRevisionIds().stream().anyMatch(id -> id != null);
        if (wikidataRequested) {
            // 이미 등록된 QID는 외부 재검증·사진 다운로드 전에 돌려보낸다.
            // 동시 등록은 저장 트랜잭션 안의 재확인과 UNIQUE 제약이 계속 막는다.
            persistenceService.rejectRegisteredWikidata(form.getWikidataQid());
        }
        String commonsSelectionJson = form.getCommonsSelectedPhotosJson();
        List<CommonsPhotoImportService.Selection> commonsSelections =
                commonsSelectionJson == null || commonsSelectionJson.isBlank()
                        ? List.of()
                        : commonsPhotoImportService.parseSelections(commonsSelectionJson, form.getWikidataQid());
        if (wikidataRequested && !selections.isEmpty()) {
            throw new IllegalArgumentException("Wikidata 등록에서는 KTO 관광사진을 함께 선택할 수 없습니다.");
        }
        long preparationStart = System.nanoTime();
        WikidataRegistrationService.PreparedRegistration prepared = wikidataRequested || wikipediaRequested
                ? wikidataRegistrationService.prepareRegistration(form, commonsSelections,
                        form.isMain() && hasDirectUpload(form.getImages()))
                : new WikidataRegistrationService.PreparedRegistration(Map.of(), List.of());
        long preparationMillis = (System.nanoTime() - preparationStart) / 1_000_000;

        List<PreparedKtoPhoto> preparedPhotos = preparePhotos(selections);
        List<PreparedCommonsPhoto> commonsPhotos = prepared.photos();
        try {
            if (wikidataRequested) {
                long persistStart = System.nanoTime();
                persistenceService.registerWikidataDestination(form, userId, prepared.sources(), commonsPhotos);
                log.info("Wikidata 여행지 등록: qid={}, 재검증·사진 준비 {}ms, DB 저장 {}ms, Wikipedia 출처 {}건, Commons 사진 {}장",
                        form.getWikidataQid(), preparationMillis, (System.nanoTime() - persistStart) / 1_000_000,
                        prepared.sources().size(), commonsPhotos.size());
            } else {
                persistenceService.registerDestination(form, userId, preparedPhotos);
            }
        } catch (RuntimeException exception) {
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            if (!commonsPhotos.isEmpty()) commonsPhotoImportService.cleanup(commonsPhotos);
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
