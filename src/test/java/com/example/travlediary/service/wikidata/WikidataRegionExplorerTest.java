package com.example.travlediary.service.wikidata;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataRegionExplorerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WikidataSparqlClient sparql = mock(WikidataSparqlClient.class);
    private final CountryCategoryService regions = mock(CountryCategoryService.class);
    private final WikidataRegionExplorer explorer = new WikidataRegionExplorer(sparql, regions);

    private final CountryCategory asia = region(1L, "아시아", "Asia", "AS", null);
    private final CountryCategory japan = region(8L, "일본", "Japan", "JP", 1L);
    private final CountryCategory fukuoka = region(55L, "후쿠오카", "Fukuoka", "JP-40", 8L);

    {
        when(regions.getOverseasRootIds()).thenReturn(List.of(1L, 2L));
        when(regions.getRegionPath(55L)).thenReturn(List.of(asia, japan, fukuoka));
        when(regions.getRegionPath(8L)).thenReturn(List.of(asia, japan));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P297 \"JP\"")))).thenReturn(rows(row("c", "Q17")));
    }

    @Test
    void subdivisionIsMappedByItsIsoCodeAndOffersItsCitiesToNarrowDown() {
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P300 \"JP-40\"") && query.contains("wd:Q17"))))
                .thenReturn(rows(row("r", "Q123258", "ko", "후쿠오카현")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P131 wd:Q123258")))).thenReturn(rows(
                row("m", "Q1136", "ko", "기타큐슈시"), row("m", "Q26600", "ko", "후쿠오카시")));

        var resolution = explorer.resolve(55L);

        assertThat(resolution.qid()).isEqualTo("Q123258");
        assertThat(resolution.wikidataName()).isEqualTo("후쿠오카현");
        assertThat(resolution.basis()).contains("ISO 3166-2", "JP-40");
        assertThat(resolution.municipalities()).extracting("qid").containsExactly("Q1136", "Q26600");
        // 같은 지역을 다시 열어도 매핑·시 목록은 캐시에서 준다.
        explorer.resolve(55L);
        verify(sparql, times(1)).select(argThat(query -> query != null && query.contains("wdt:P300")));
        verify(sparql, times(1)).select(argThat(query -> query != null && query.contains("wdt:P131 wd:Q123258")));
    }

    @Test
    void cityRegionsNeedAUniqueNameMatchInsideTheCountryOtherwiseTheyAreNotGuessed() {
        CountryCategory indonesia = region(30L, "인도네시아", "Indonesia", "ID", 1L);
        CountryCategory bali = region(301L, "발리", "Bali", "ID-DPS", 30L);
        CountryCategory jakarta = region(302L, "자카르타", "Jakarta", "ID-JKT", 30L);
        when(regions.getRegionPath(301L)).thenReturn(List.of(asia, indonesia, bali));
        when(regions.getRegionPath(302L)).thenReturn(List.of(asia, indonesia, jakarta));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P297 \"ID\"")))).thenReturn(rows(row("c", "Q252")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P300")))).thenReturn(rows());
        // 발리: 한국어가 정확히 같은 곳은 없고 영어 이름이 같은 곳이 둘이다.
        when(sparql.select(argThat(query -> query != null && query.contains("\"발리\"@ko")))).thenReturn(rows(
                row("r", "Q25290900", "en", "Bali"), row("r", "Q25471356", "en", "Bali"),
                row("r", "Q3125978", "ko", "발리주", "en", "Bali Province")));
        when(sparql.select(argThat(query -> query != null && query.contains("\"자카르타\"@ko")))).thenReturn(rows(
                row("r", "Q3630", "ko", "자카르타", "en", "Jakarta"), row("r", "Q999", "en", "Jakarta")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P131 wd:Q3630")))).thenReturn(rows());

        var ambiguous = explorer.resolve(301L);
        var unique = explorer.resolve(302L);

        assertThat(ambiguous.qid()).isNull();
        assertThat(ambiguous.message()).contains("여러 곳");
        assertThat(unique.qid()).isEqualTo("Q3630");
        assertThat(unique.basis()).contains("이름이 유일하게 일치");
        assertThat(unique.municipalities()).isEmpty();
        assertThatThrownBy(() -> explorer.places(301L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countryWideDomesticAndUnlistedCityRequestsAreRefusedBeforeQueryingPlaces() {
        CountryCategory korea = region(7L, "대한민국", "South Korea", "KR", null);
        CountryCategory seoul = region(38L, "서울", "Seoul", "KR-11", 7L);
        when(regions.getRegionPath(38L)).thenReturn(List.of(korea, seoul));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P300 \"JP-40\""))))
                .thenReturn(rows(row("r", "Q123258", "ko", "후쿠오카현")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P131 wd:Q123258"))))
                .thenReturn(rows(row("m", "Q26600", "ko", "후쿠오카시")));

        assertThatThrownBy(() -> explorer.resolve(8L)).hasMessageContaining("국가 전체");
        assertThatThrownBy(() -> explorer.resolve(38L)).hasMessageContaining("해외 지역");
        assertThatThrownBy(() -> explorer.places(55L, "Q64")).hasMessageContaining("속한 시가 아닙니다");
        verify(sparql, never()).select(argThat(query -> query != null && query.contains("wdt:P131*")));
    }

    @Test
    void placesAreQueriedForTheChosenCityOnceAndKeepTheirPopularityOrder() {
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P300 \"JP-40\""))))
                .thenReturn(rows(row("r", "Q123258", "ko", "후쿠오카현")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P131 wd:Q123258"))))
                .thenReturn(rows(row("m", "Q26600", "ko", "후쿠오카시")));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P131* wd:Q26600"))))
                .thenReturn(rows(row("item", "Q847033"), row("item", "Q1050353"), row("item", "Q847033")));

        var places = explorer.places(55L, "q26600");
        explorer.places(55L, "Q26600");

        assertThat(places.areaQid()).isEqualTo("Q26600");
        assertThat(places.qids()).containsExactly("Q847033", "Q1050353");
        assertThat(places.limited()).isFalse();
        verify(sparql, times(1)).select(argThat(query -> query != null && query.contains("wdt:P131* wd:Q26600")));
        // 여행 관련 분류(하위 분류 포함)나 문화재 지정으로 거른다.
        verify(sparql).select(argThat(query -> query != null && query.contains("wdt:P31/wdt:P279* ?class")
                && query.contains("wd:Q570116") && query.contains("wdt:P1435")));
    }

    @Test
    void namesThatCouldBreakTheQueryAreNeverPutIntoIt() {
        CountryCategory odd = region(90L, "이상한\"지역", "Odd\\Place", "JP-99", 8L);
        when(regions.getRegionPath(90L)).thenReturn(List.of(asia, japan, odd));
        when(sparql.select(argThat(query -> query != null && query.contains("wdt:P300")))).thenReturn(rows());

        var resolution = explorer.resolve(90L);

        assertThat(resolution.qid()).isNull();
        assertThat(resolution.message()).contains("이름이 없어");
        verify(sparql, never()).select(argThat(query -> query != null && query.contains("rdfs:label \"")));
    }

    private CountryCategory region(Long id, String name, String english, String code, Long parentId) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setNameEn(english);
        region.setCode(code);
        region.setParentId(parentId);
        return region;
    }

    private JsonNode rows(ObjectNode... rows) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode row : rows) array.add(row);
        return array;
    }

    /** SPARQL 결과 한 줄. 첫 쌍은 엔티티 변수와 QID, 나머지는 문자열 변수와 값. */
    private ObjectNode row(String variable, String qid, String... pairs) {
        ObjectNode row = mapper.createObjectNode();
        row.putObject(variable).put("type", "uri").put("value", "http://www.wikidata.org/entity/" + qid);
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            row.putObject(pairs[index]).put("type", "literal").put("value", pairs[index + 1]);
        }
        return row;
    }
}
