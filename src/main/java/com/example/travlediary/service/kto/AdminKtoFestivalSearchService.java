package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoFestivalSearchItemResponse;
import com.example.travlediary.dto.kto.KtoFestivalSearchResponse;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminKtoFestivalSearchService {

    private static final String KTO_SOURCE_TYPE = "KTO_TOURAPI";
    private static final int SCAN_PAGE_SIZE = 20;

    private final KtoFestivalService ktoFestivalService;
    private final FestivalInfoMapper festivalInfoMapper;

    public KtoFestivalSearchResponse search(LocalDate startDate, LocalDate endDate,
                                            int pageNo, int numOfRows, boolean unregisteredOnly) {
        return searchPages((page, rows) -> ktoFestivalService.search(startDate, endDate, page, rows),
                pageNo, numOfRows, unregisteredOnly);
    }

    public KtoFestivalSearchResponse searchByKeyword(String keyword, int pageNo,
                                                     int numOfRows, boolean unregisteredOnly) {
        return searchPages((page, rows) -> ktoFestivalService.searchByKeyword(keyword, page, rows),
                pageNo, numOfRows, unregisteredOnly);
    }

    private KtoFestivalSearchResponse searchPages(BiFunction<Integer, Integer, KtoFestivalSearchResponse> fetchPage,
                                                  int pageNo, int numOfRows, boolean unregisteredOnly) {
        if (!unregisteredOnly) {
            KtoFestivalSearchResponse response = fetchPage.apply(pageNo, numOfRows);
            return new KtoFestivalSearchResponse(response.pageNo(), numOfRows,
                    response.totalCount(), enrich(response.items()));
        }

        long offset = (long) (pageNo - 1) * numOfRows;
        List<KtoFestivalSearchItemResponse> selected = new ArrayList<>(numOfRows);
        int matchingCount = 0;
        int rawPage = 1;
        Integer rawTotalCount = null;
        do {
            KtoFestivalSearchResponse response = fetchPage.apply(rawPage, SCAN_PAGE_SIZE);
            if (response == null || response.pageNo() != rawPage
                    || response.totalCount() < 0
                    || response.items() == null || response.items().size() > SCAN_PAGE_SIZE
                    || response.totalCount() < response.items().size()) {
                throw KtoTourApiException.upstreamFailure();
            }
            if (rawTotalCount == null) {
                rawTotalCount = response.totalCount();
            } else if (rawTotalCount != response.totalCount()) {
                throw KtoTourApiException.upstreamFailure();
            }
            long remaining = Math.max(0L, (long) rawTotalCount - (long) (rawPage - 1) * SCAN_PAGE_SIZE);
            int expectedRows = (int) Math.min(SCAN_PAGE_SIZE, remaining);
            // TourAPI reports the actual row count on a short final page.
            if (response.numOfRows() < 0 || response.numOfRows() > SCAN_PAGE_SIZE
                    || (expectedRows > 0 && response.numOfRows() != SCAN_PAGE_SIZE
                    && (expectedRows == SCAN_PAGE_SIZE || response.numOfRows() != expectedRows))) {
                throw KtoTourApiException.upstreamFailure();
            }
            if (response.items().isEmpty() && rawTotalCount > 0) {
                throw KtoTourApiException.upstreamFailure();
            }
            for (KtoFestivalSearchItemResponse item : enrich(response.items())) {
                if (!"UNREGISTERED".equals(item.registrationStatus())) continue;
                if (matchingCount >= offset && selected.size() < numOfRows) selected.add(item);
                matchingCount++;
            }
            rawPage++;
        } while ((long) (rawPage - 1) * SCAN_PAGE_SIZE < rawTotalCount);

        return new KtoFestivalSearchResponse(pageNo, numOfRows, matchingCount, List.copyOf(selected));
    }

    private List<KtoFestivalSearchItemResponse> enrich(List<KtoFestivalSearchItemResponse> items) {
        List<String> contentIds = items.stream()
                .filter(item -> item.contentId() != null && !item.contentId().isBlank()
                        && item.eventStartDate() != null)
                .map(KtoFestivalSearchItemResponse::contentId)
                .distinct()
                .toList();
        Map<Occurrence, Long> registered = contentIds.isEmpty() ? Map.of()
                : festivalInfoMapper.findOccurrencesByContentIds(KTO_SOURCE_TYPE, contentIds).stream()
                .collect(Collectors.toMap(
                        info -> new Occurrence(info.getExternalContentId(), info.getEventYear()),
                        FestivalInfo::getInfoId));

        return items.stream().map(item -> {
            if (item.contentId() == null || item.contentId().isBlank() || item.eventStartDate() == null) {
                return item.withRegistration("UNKNOWN", null);
            }
            Long registeredId = registered.get(new Occurrence(item.contentId(), item.eventStartDate().getYear()));
            return registeredId == null ? item.withRegistration("UNREGISTERED", null)
                    : item.withRegistration("REGISTERED", registeredId);
        }).toList();
    }

    private record Occurrence(String contentId, Integer eventYear) {
    }
}
