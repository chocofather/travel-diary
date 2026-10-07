package com.tripbora.service.wikidata;

import com.tripbora.dto.wikidata.WikidataDestinationCandidate;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateQuery;
import com.tripbora.service.destination.DestinationDuplicateService;
import com.tripbora.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wikidata 후보(등록폼 단건 검색·해외 일괄 등록 목록)를 공통 중복 판별({@link DestinationDuplicateService})에 넘긴다.
 * 후보의 언어별 이름·좌표를 판별 입력으로 옮기는 일만 하고 판정 규칙은 갖지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WikidataDuplicateMarker {

    private final WikidataDestinationService wikidataDestinationService;
    private final DestinationDuplicateService duplicateService;

    /** 단건 검색 상세 결과에 판별 결과를 붙인다. 지역은 모르므로 이름·좌표·QID로만 본다. */
    public List<WikidataDestinationCandidate> mark(List<WikidataDestinationCandidate> places) {
        if (places == null || places.isEmpty()) {
            return places == null ? List.of() : places;
        }
        List<DestinationDuplicateCheck> checks = check(places, Map.of(), null);
        List<WikidataDestinationCandidate> marked = new ArrayList<>(places.size());
        for (int index = 0; index < places.size(); index++) {
            marked.add(places.get(index).withDuplicate(checks.get(index)));
        }
        return List.copyOf(marked);
    }

    /**
     * 후보마다 판별한다. 결과는 places 순서와 같다.
     *
     * @param fallbacks 검색 목록의 최소 정보(상세 조회에 이름이 없을 때 쓰는 이름)
     * @param regionId  후보를 찾은 TripBora 지역. 모르면 null
     */
    public List<DestinationDuplicateCheck> check(List<WikidataDestinationCandidate> places,
                                                 Map<String, WikidataDestinationCandidate> fallbacks,
                                                 Long regionId) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }
        // 검색 상세가 방금 받아 둔 캐시 엔티티를 읽으므로 외부 호출이 늘지 않는다.
        Map<String, WikidataDestinationService.PlaceHints> hints =
                wikidataDestinationService.placeHints(places.stream().map(WikidataDestinationCandidate::qid).toList());
        Map<String, WikidataDestinationService.PlaceHints> safeHints = hints == null ? Map.of() : hints;
        return duplicateService.checkAll(places.stream()
                .map(place -> query(place, fallbacks.get(place.qid()), safeHints.get(place.qid()), regionId))
                .toList());
    }

    private DestinationDuplicateQuery query(WikidataDestinationCandidate place,
                                            WikidataDestinationCandidate fallback,
                                            WikidataDestinationService.PlaceHints hint, Long regionId) {
        List<String> names = new ArrayList<>();
        if (hint != null && hint.names() != null) names.addAll(hint.names());
        names.add(place.name());
        if (fallback != null) names.add(fallback.name());
        return new DestinationDuplicateQuery(DestinationService.WIKIDATA_SOURCE_TYPE, place.qid(), null, names,
                regionId,
                hint == null || hint.latitude() == null ? null : BigDecimal.valueOf(hint.latitude()),
                hint == null || hint.longitude() == null ? null : BigDecimal.valueOf(hint.longitude()));
    }
}
