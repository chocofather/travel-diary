package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.service.kto.InvalidKtoSelectedPhotosException;
import com.tripbora.service.kto.KtoPhotoImportService;
import com.tripbora.service.kto.PreparedKtoPhoto;
import com.tripbora.service.wikidata.CommonsPhotoImportService;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import com.tripbora.service.wikidata.WikidataRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationSaveOrchestrationService {

    private final KtoPhotoImportService ktoPhotoImportService;
    private final DestinationSavePersistenceService persistenceService;
    @Autowired private WikidataRegistrationService wikidataRegistrationService;
    @Autowired private CommonsPhotoImportService commonsPhotoImportService;
    @Autowired private DestinationDuplicateService duplicateService;

    public void registerDestination(DestinationForm form,
                                    Long userId,
                                    List<KtoSelectedPhotoRequest> selectedPhotos) {
        register(form, userId, selectedPhotos, false);
    }

    /**
     * JSON 일괄등록 전용 등록. 등록폼·KTO·Wikidata 와 같은 경로로 저장하되, 외부 ID(QID·contentId)가 없는
     * 관리자 입력형 여행지도 저장 직전에 공통 중복 판별을 거친다. 사진은 받지 않는다.
     * 기존 등록 경로({@link #registerDestination})의 동작은 바꾸지 않는다.
     *
     * @return 저장된 여행지 번호
     * @throws DuplicateDestinationException 확정 중복이거나, 확인하지 않은 중복 가능성이 있는 경우
     */
    public Long registerImportedDestination(DestinationForm form, Long userId) {
        return register(form, userId, List.of(), true);
    }

    /**
     * @param imported JSON 일괄등록이면 true. 외부 ID가 없어도 중복 판별을 하고, 저장된 여행지 번호를 돌려준다.
     * @return 저장된 여행지 번호. 기존 관리자 직접 등록(imported=false, 외부 ID 없음)은 null
     */
    private Long register(DestinationForm form,
                          Long userId,
                          List<KtoSelectedPhotoRequest> selectedPhotos,
                          boolean imported) {
        List<KtoSelectedPhotoRequest> selections = safeSelections(selectedPhotos);
        validateCreateMainSelection(form, selections);

        boolean wikidataRequested = form.getWikidataQid() != null && !form.getWikidataQid().isBlank();
        String ktoContentId = normalizeKtoContentId(form.getKtoContentId());
        if (wikidataRequested && ktoContentId != null) {
            throw new IllegalArgumentException("Wikidata 후보와 TourAPI 후보를 함께 연결할 수 없습니다. 하나만 선택해 주세요.");
        }
        boolean wikipediaRequested = form.getWikipediaRevisionIds() != null
                && form.getWikipediaRevisionIds().stream().anyMatch(id -> id != null);
        if (wikidataRequested) {
            // 이미 등록된 QID는 외부 재검증·사진 다운로드 전에 돌려보낸다.
            // 동시 등록은 저장 트랜잭션 안의 재확인과 UNIQUE 제약이 계속 막는다.
            persistenceService.rejectRegisteredWikidata(form.getWikidataQid());
        }
        if (wikidataRequested || ktoContentId != null) {
            // 외부 후보(Wikidata·TourAPI)로 채운 등록은 목록과 같은 공통 판별을 저장 직전에 다시 한다.
            // 외부 재검증·사진 다운로드 전에 돌려보내 아무것도 남기지 않는다. 완전 수동 등록은 막지 않는다.
            rejectDuplicate(form, wikidataRequested
                    ? DestinationService.WIKIDATA_SOURCE_TYPE : DestinationService.KTO_TOUR_API_SOURCE_TYPE,
                    wikidataRequested ? form.getWikidataQid().strip().toUpperCase(Locale.ROOT) : ktoContentId);
        } else if (imported) {
            // JSON 일괄등록은 외부 ID가 없어도 이름·위치·Place ID 로 같은 판별을 한다.
            rejectDuplicate(form, null, null);
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
                Long destinationId = persistenceService.registerWikidataDestination(
                        form, userId, prepared.sources(), commonsPhotos);
                log.info("Wikidata 여행지 등록: qid={}, 재검증·사진 준비 {}ms, DB 저장 {}ms, Wikipedia 출처 {}건, Commons 사진 {}장",
                        form.getWikidataQid(), preparationMillis, (System.nanoTime() - persistStart) / 1_000_000,
                        prepared.sources().size(), commonsPhotos.size());
                return destinationId;
            }
            // TourAPI 후보를 고른 등록은 contentId 를 남겨, 이후 TourAPI 목록에서 같은 곳을 등록됨으로 본다.
            // contentId 가 없으면 지금처럼 관리자 직접 등록(ADMIN)이다.
            if (ktoContentId == null && !imported) {
                persistenceService.registerDestination(form, userId, preparedPhotos);
                return null;
            }
            return persistenceService.registerDestination(form, userId, ktoContentId, preparedPhotos);
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

    /**
     * 확정 중복은 항상, 중복 가능성은 관리자가 확인하지 않았을 때 저장하지 않는다.
     *
     * @throws DuplicateDestinationException 판별 결과와 기존 여행지를 담아 돌려보낸다
     */
    private void rejectDuplicate(DestinationForm form, String sourceType, String externalContentId) {
        DestinationDuplicateCheck check = duplicateService.check(
                DestinationDuplicateQuery.fromForm(form, sourceType, externalContentId));
        if (check.confirmed() || (check.needsReview() && !form.isAllowPossibleDuplicate())) {
            throw new DuplicateDestinationException(check);
        }
    }

    /** TourAPI contentId 는 숫자다. 비어 있으면 null, 형식이 다르면 저장하지 않고 입력 오류로 돌려보낸다. */
    private String normalizeKtoContentId(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return null;
        }
        String normalized = contentId.strip();
        if (!KTO_CONTENT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("TourAPI contentId 형식이 올바르지 않습니다. TourAPI 후보를 다시 선택해 주세요.");
        }
        return normalized;
    }

    private static final Pattern KTO_CONTENT_ID = Pattern.compile("[0-9]{1,30}");

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
