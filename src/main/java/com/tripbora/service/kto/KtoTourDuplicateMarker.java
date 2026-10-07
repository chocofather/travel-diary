package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoTourAreaCandidateResponse;
import com.tripbora.dto.kto.KtoTourRegionMatchResponse;
import com.tripbora.dto.kto.KtoTourSearchItemResponse;
import com.tripbora.dto.kto.KtoTourSearchResponse;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateQuery;
import com.tripbora.service.destination.DestinationDuplicateService;
import com.tripbora.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TourAPI 후보(지역별 일괄 목록·등록폼 검색)를 공통 중복 판별({@link DestinationDuplicateService})에 넘기고
 * 결과를 후보에 붙인다. TourAPI 값을 판별 입력으로 옮기는 일만 하고 판정 규칙은 갖지 않는다.
 */
@Component
@RequiredArgsConstructor
public class KtoTourDuplicateMarker {

    /** 주소 앞 몇 단어로 지역 매칭 결과를 재사용할지. 국내 지역 계층(시/도·시/군/구·구)보다 넉넉하다. */
    private static final int REGION_ADDRESS_TOKENS = 4;

    private final DestinationDuplicateService duplicateService;
    private final KtoTourRegionMatchService regionMatchService;

    public List<KtoTourAreaCandidateResponse> markAreaCandidates(List<KtoTourAreaCandidateResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        RegionLookup regions = new RegionLookup();
        List<DestinationDuplicateQuery> queries = candidates.stream()
                .map(candidate -> query(candidate.contentId(), candidate.title(), candidate.address(),
                        candidate.longitude(), candidate.latitude(), regions))
                .toList();
        List<DestinationDuplicateCheck> checks = duplicateService.checkAll(queries);
        List<KtoTourAreaCandidateResponse> marked = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            marked.add(candidates.get(index).withDuplicate(checks.get(index)));
        }
        return List.copyOf(marked);
    }

    public KtoTourSearchResponse markSearch(KtoTourSearchResponse response) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return response;
        }
        RegionLookup regions = new RegionLookup();
        List<KtoTourSearchItemResponse> items = response.items();
        List<DestinationDuplicateCheck> checks = duplicateService.checkAll(items.stream()
                .map(item -> query(item.contentId(), item.title(), item.address(),
                        item.longitude(), item.latitude(), regions))
                .toList());
        List<KtoTourSearchItemResponse> marked = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            marked.add(items.get(index).withDuplicate(checks.get(index)));
        }
        return new KtoTourSearchResponse(response.pageNo(), response.numOfRows(), response.totalCount(),
                List.copyOf(marked));
    }

    private DestinationDuplicateQuery query(String contentId, String title, String address,
                                            String longitude, String latitude, RegionLookup regions) {
        return new DestinationDuplicateQuery(DestinationService.KTO_TOUR_API_SOURCE_TYPE, contentId, null,
                title == null ? List.of() : List.of(title), regions.regionId(address),
                decimal(latitude), decimal(longitude));
    }

    /** TourAPI 좌표 문자열. 읽을 수 없거나 0 이면(좌표 없음 표기) null. */
    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.strip());
            return decimal.signum() == 0 ? null : decimal;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 한 번의 목록 판별 안에서 같은 시/군/구 주소는 지역 매칭을 다시 하지 않는다. */
    private final class RegionLookup {
        private final Map<String, Long> byAddressPrefix = new HashMap<>();

        private Long regionId(String address) {
            if (address == null || address.isBlank()) {
                return null;
            }
            String[] tokens = address.strip().split("\\s+");
            String prefix = String.join(" ", Arrays.copyOf(tokens, Math.min(tokens.length, REGION_ADDRESS_TOKENS)));
            // 매칭되지 않은 주소(null)도 기억해야 같은 주소를 다시 매칭하지 않는다.
            if (byAddressPrefix.containsKey(prefix)) {
                return byAddressPrefix.get(prefix);
            }
            KtoTourRegionMatchResponse match = regionMatchService.match(prefix);
            Long regionId = match != null && match.matched() ? match.deepestRegionId() : null;
            byAddressPrefix.put(prefix, regionId);
            return regionId;
        }
    }
}
