package com.example.travlediary.service.kto;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.kto.KtoTourAreaCandidateListResponse;
import com.example.travlediary.dto.kto.KtoTourAreaCandidateResponse;
import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.dto.kto.KtoTourBulkImportRequest;
import com.example.travlediary.dto.kto.KtoTourBulkImportResponse;
import com.example.travlediary.service.destination.DestinationSavePersistenceService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.destination.DuplicateTourApiDestinationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 지역별 TourAPI 여행지 후보 조회 → contentId 중복 확인 → 선택 항목 등록.
 * 조회 결과를 그 자리에서 저장하지 않고, 관리자가 고른 항목만 {@link #importSelected} 로 저장한다.
 *
 * <p>항목별로 독립 트랜잭션을 쓰므로 한 건이 실패해도 나머지 등록은 그대로 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KtoTourBulkImportService {

    private static final String UNMATCHED_REGION_MESSAGE =
            "주소로 지역을 찾지 못했습니다. 단건 등록 화면에서 지역을 직접 선택해 주세요.";
    private static final String UPSTREAM_FAILURE_MESSAGE = "TourAPI 상세정보를 불러오지 못했습니다.";
    private static final String UNEXPECTED_FAILURE_MESSAGE = "여행지를 저장하지 못했습니다.";

    private final KtoTourService ktoTourService;
    private final KtoTourAreaCandidateCache candidateCache;
    private final KtoTourDetailLookupService ktoTourDetailLookupService;
    private final KtoTourDestinationFormMapper ktoTourDestinationFormMapper;
    private final KtoTourImageImportService ktoTourImageImportService;
    private final KtoPhotoImportService ktoPhotoImportService;
    private final DestinationService destinationService;
    private final DestinationSavePersistenceService destinationSavePersistenceService;

    /**
     * 후보 조회. 순서가 중요하다.
     * <ol>
     *   <li>선택한 유형(또는 전체 4종)의 후보를 TourAPI 페이지 끝까지 모아 합친다</li>
     *   <li>contentId 기준 중복 제거 후 제목순 정렬</li>
     *   <li>contentId 로 DB 등록 여부 판정 (1차 중복 확인)</li>
     *   <li>등록상태 필터 적용</li>
     *   <li>마지막에 우리 서버 기준으로 페이징</li>
     * </ol>
     * 페이징이 맨 뒤라서, 특정 TourAPI 페이지에 등록완료 항목이 없다고 등록완료 탭이 비지 않는다.
     */
    public KtoTourAreaCandidateListResponse findCandidates(String regionCode, String subRegionCode,
                                                           String contentTypeId,
                                                           KtoTourCandidateRegistrationFilter filter,
                                                           int pageNo, int numOfRows) {
        List<KtoTourAreaCandidateResponse> merged =
                mergedCandidates(regionCode, subRegionCode, contentTypeId);

        Set<String> registeredContentIds = destinationService.findRegisteredTourApiContentIds(
                merged.stream().map(KtoTourAreaCandidateResponse::contentId).toList());
        List<KtoTourAreaCandidateResponse> withRegistration = merged.stream()
                .map(candidate -> candidate.withRegistered(
                        registeredContentIds.contains(candidate.contentId())))
                .toList();

        int registeredCount = (int) withRegistration.stream()
                .filter(KtoTourAreaCandidateResponse::registered).count();
        List<KtoTourAreaCandidateResponse> filtered = withRegistration.stream()
                .filter(candidate -> filter.accepts(candidate.registered()))
                .toList();

        int fromIndex = Math.min((pageNo - 1) * numOfRows, filtered.size());
        int toIndex = Math.min(fromIndex + numOfRows, filtered.size());
        return new KtoTourAreaCandidateListResponse(
                pageNo,
                numOfRows,
                filtered.size(),
                withRegistration.size(),
                withRegistration.size() - registeredCount,
                registeredCount,
                filtered.subList(fromIndex, toIndex));
    }

    /**
     * 유형을 고르면 그 유형만, 비우면 지원하는 네 유형을 각각 조회해 하나의 목록으로 합친다.
     * 같은 조건의 TourAPI 재호출은 캐시가 막는다.
     */
    private List<KtoTourAreaCandidateResponse> mergedCandidates(String regionCode,
                                                                String subRegionCode,
                                                                String contentTypeId) {
        List<KtoTourImportContentType> targetTypes = KtoTourImportContentType
                .fromContentTypeId(contentTypeId)
                .map(List::of)
                .orElseGet(KtoTourImportContentType::supported);

        // contentId 가 겹치면 먼저 읽은 쪽을 남긴다.
        Map<String, KtoTourAreaCandidateResponse> byContentId = new LinkedHashMap<>();
        for (KtoTourImportContentType type : targetTypes) {
            KtoTourAreaCandidateCache.Key key = new KtoTourAreaCandidateCache.Key(
                    regionCode, subRegionCode, type.contentTypeId());
            List<KtoTourAreaCandidateResponse> candidates = candidateCache.get(key,
                    () -> ktoTourService.fetchAllByArea(regionCode, subRegionCode, type));
            for (KtoTourAreaCandidateResponse candidate : candidates) {
                byContentId.putIfAbsent(candidate.contentId(), candidate);
            }
        }

        Collator koreanTitleOrder = Collator.getInstance(Locale.KOREAN);
        return byContentId.values().stream()
                .sorted(Comparator.comparing(KtoTourAreaCandidateResponse::title, koreanTitleOrder)
                        .thenComparing(KtoTourAreaCandidateResponse::contentId))
                .toList();
    }

    public KtoTourBulkImportResponse importSelected(List<KtoTourBulkImportRequest.Item> items,
                                                    Long userId) {
        List<KtoTourBulkImportResponse.ItemResult> results = new ArrayList<>();
        // 같은 요청에 같은 contentId 가 두 번 들어와도 한 번만 저장한다.
        Set<String> handledContentIds = new LinkedHashSet<>();
        for (KtoTourBulkImportRequest.Item item : items) {
            String contentId = item.contentId().strip();
            if (!handledContentIds.add(contentId)) {
                continue;
            }
            results.add(importOne(contentId, item.contentTypeId().strip(), userId));
        }
        return KtoTourBulkImportResponse.of(results);
    }

    private KtoTourBulkImportResponse.ItemResult importOne(String contentId, String contentTypeId,
                                                           Long userId) {
        // 1차: 상세조회 전에 이미 등록된 항목이면 바깥 API 호출도 하지 않는다.
        if (destinationService.existsTourApiDestination(contentId)) {
            return KtoTourBulkImportResponse.ItemResult.duplicate(contentId, null);
        }

        KtoTourAutofillResponse detail;
        try {
            detail = ktoTourDetailLookupService.lookup(contentId, contentTypeId);
        } catch (KtoTourApiException exception) {
            return KtoTourBulkImportResponse.ItemResult.failed(contentId, null, UPSTREAM_FAILURE_MESSAGE);
        }

        String title = detail.title();
        if (detail.regionMatch() == null || !detail.regionMatch().matched()) {
            return KtoTourBulkImportResponse.ItemResult.failed(contentId, title, UNMATCHED_REGION_MESSAGE);
        }

        DestinationForm form;
        try {
            form = ktoTourDestinationFormMapper.toForm(detail, detail.regionMatch().deepestRegionId());
        } catch (KtoTourBulkImportException exception) {
            return KtoTourBulkImportResponse.ItemResult.failed(contentId, title, exception.getMessage());
        }

        List<PreparedKtoPhoto> preparedPhotos = ktoTourImageImportService.preparePhotos(contentId, title);
        try {
            Long destinationId = destinationSavePersistenceService.registerDestination(
                    form, userId, contentId, preparedPhotos);
            return KtoTourBulkImportResponse.ItemResult.success(contentId, title, destinationId);
        } catch (DuplicateTourApiDestinationException | DuplicateKeyException exception) {
            // 2차 중복 검사 또는 DB 유니크 제약에 걸린 경우. 저장 실패가 아니라 건너뜀으로 센다.
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            return KtoTourBulkImportResponse.ItemResult.duplicate(contentId, title);
        } catch (RuntimeException exception) {
            ktoPhotoImportService.cleanupPreparedPhotos(preparedPhotos);
            log.warn("TourAPI 여행지 일괄등록 실패 (contentId={}, 원인={})",
                    contentId, exception.getClass().getSimpleName());
            return KtoTourBulkImportResponse.ItemResult.failed(contentId, title, UNEXPECTED_FAILURE_MESSAGE);
        }
    }
}
