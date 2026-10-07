package com.tripbora.service.wikidata;

import com.tripbora.dto.wikidata.WikidataDestinationCandidate;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateQuery;
import com.tripbora.service.destination.DestinationDuplicateReason;
import com.tripbora.service.destination.DestinationDuplicateService;
import com.tripbora.service.destination.DestinationDuplicateStatus;
import com.tripbora.service.destination.DestinationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 등록폼 단건 Wikidata 검색 후보에 일괄 등록과 같은 공통 중복 판별을 붙인다. */
class WikidataDuplicateMarkerTest {

    private final WikidataDestinationService wikidata = mock(WikidataDestinationService.class);
    private final DestinationDuplicateService duplicates = mock(DestinationDuplicateService.class);
    private final WikidataDuplicateMarker marker = new WikidataDuplicateMarker(wikidata, duplicates);

    @Test
    @SuppressWarnings("unchecked")
    void singleSearchCandidatesCarryTheCommonResultFromAllLanguageNamesAndCoordinates() {
        List<WikidataDestinationCandidate> places = List.of(
                new WikidataDestinationCandidate("Q484637", "경복궁", "ko", "조선의 법궁", "ko", "대한민국", "서울", null, null),
                new WikidataDestinationCandidate("Q1", "새 장소", "ko", null, null, "일본", null, null, null));
        when(wikidata.placeHints(List.of("Q484637", "Q1"))).thenReturn(Map.of(
                "Q484637", new WikidataDestinationService.PlaceHints(List.of("경복궁", "Gyeongbokgung"), 37.5796, 126.977)));
        DestinationDuplicateCheck possible = new DestinationDuplicateCheck(DestinationDuplicateStatus.POSSIBLE_DUPLICATE,
                DestinationDuplicateReason.NAME_AND_NEARBY, 5L, "경복궁", 70, "같은 이름 · 가까운 위치 (약 70m)");
        when(duplicates.checkAll(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(possible, DestinationDuplicateCheck.NOT_REGISTERED));

        List<WikidataDestinationCandidate> marked = marker.mark(places);

        assertThat(marked).extracting(WikidataDestinationCandidate::qid).containsExactly("Q484637", "Q1");
        assertThat(marked.get(0).duplicate()).isEqualTo(possible);
        assertThat(marked.get(0).country()).isEqualTo("대한민국");
        assertThat(marked.get(1).duplicate().status()).isEqualTo(DestinationDuplicateStatus.NOT_REGISTERED);
        ArgumentCaptor<List<DestinationDuplicateQuery>> queries = ArgumentCaptor.forClass(List.class);
        verify(duplicates).checkAll(queries.capture());
        DestinationDuplicateQuery palace = queries.getValue().get(0);
        assertThat(palace.sourceType()).isEqualTo(DestinationService.WIKIDATA_SOURCE_TYPE);
        assertThat(palace.externalContentId()).isEqualTo("Q484637");
        assertThat(palace.names()).contains("경복궁", "Gyeongbokgung");
        assertThat(palace.latitude()).isEqualByComparingTo("37.5796");
        // 단건 검색은 지역을 모르므로 이름·좌표·QID로만 본다.
        assertThat(palace.regionId()).isNull();
        // 좌표 단서가 없는 후보도 이름·QID로 판별한다.
        assertThat(queries.getValue().get(1).names()).containsExactly("새 장소");
        assertThat(queries.getValue().get(1).latitude()).isNull();
    }

    @Test
    void anEmptySearchDoesNotReadTheIndex() {
        assertThat(marker.mark(List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(duplicates, wikidata);
    }
}
