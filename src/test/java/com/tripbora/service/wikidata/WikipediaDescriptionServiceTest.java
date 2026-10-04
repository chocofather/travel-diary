package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikipediaDescriptionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesExactSitelinksAndKeepsChineseVariantsSeparate() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getWikipediaSitelinks("Q243")).thenReturn(Map.of(
                "enwiki", "Eiffel Tower", "zhwiki", "艾菲爾鐵塔"));
        when(wikipedia.getPage("en", "Eiffel Tower", null)).thenReturn(page("Q243", "Eiffel Tower", "en", "Eiffel Tower text", "https://en.wikipedia.org/wiki/Eiffel_Tower"));
        when(wikipedia.getPage("zh", "艾菲爾鐵塔", "zh-cn")).thenReturn(page("Q243", "艾菲尔铁塔", "zh", "简体说明", "https://zh.wikipedia.org/wiki/艾菲爾鐵塔"));
        when(wikipedia.getPage("zh", "艾菲爾鐵塔", "zh-tw")).thenReturn(page("Q243", "艾菲爾鐵塔", "zh", "繁體說明", "https://zh.wikipedia.org/wiki/艾菲爾鐵塔"));

        var result = service(wikidata, wikipedia).preview("Q243");

        assertThat(result.languages()).hasSize(5);
        assertThat(result.languages().stream().filter(item -> item.status().equals("AVAILABLE"))).hasSize(3);
        assertThat(result.languages().get(0).status()).isEqualTo("NO_SITELINK");
        assertThat(result.languages().get(3).description()).isEqualTo("简体说明");
        assertThat(result.languages().get(3).sourceLanguage()).isEqualTo("zh");
        assertThat(result.languages().get(3).sourceUrl()).contains("variant=zh-cn");
        assertThat(result.languages().get(4).description()).isEqualTo("繁體說明");
        assertThat(result.languages().get(1).licenseName()).contains("Attribution");
    }

    @Test
    void refusesMismatchedQidAndPreservesOtherLanguageOnApiFailure() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getWikipediaSitelinks("Q243")).thenReturn(Map.of("kowiki", "에펠탑", "enwiki", "Eiffel Tower"));
        when(wikipedia.getPage("ko", "에펠탑", null)).thenReturn(page("Q999", "에펠탑", "ko", "wrong", "https://ko.wikipedia.org/wiki/에펠탑"));
        when(wikipedia.getPage("en", "Eiffel Tower", null)).thenThrow(new WikipediaApiException("Wikipedia 요청이 많습니다. 잠시 후 다시 시도해 주세요."));

        var result = service(wikidata, wikipedia).preview("Q243");

        assertThat(result.languages().get(0).status()).isEqualTo("QID_MISMATCH");
        assertThat(result.languages().get(0).description()).isNull();
        assertThat(result.languages().get(1).status()).isEqualTo("ERROR");
        assertThat(result.languages().get(1).message()).contains("잠시 후");
    }

    @Test
    void loadsIndependentLanguagePagesWithBoundedParallelism() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getWikipediaSitelinks("Q243")).thenReturn(Map.of(
                "kowiki", "에펠탑", "enwiki", "Eiffel Tower", "jawiki", "エッフェル塔",
                "zhwiki", "艾菲爾鐵塔"));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(wikipedia.getPage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenAnswer(invocation -> {
                    int running = inFlight.incrementAndGet();
                    peak.accumulateAndGet(running, Math::max);
                    try {
                        Thread.sleep(80);
                        String lang = invocation.getArgument(0);
                        return page("Q243", invocation.getArgument(1), lang,
                                "description", "https://" + lang + ".wikipedia.org/wiki/Test");
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });

        var result = service(wikidata, wikipedia).preview("Q243");

        assertThat(result.languages()).hasSize(5);
        // 다섯 언어 요청이 두 번에 나뉘지 않고 한 번에 진행되되, 풀 크기(5)를 넘지 않는다.
        assertThat(peak.get()).isBetween(3, 5);
    }

    @Test
    void autofillReadsSitelinksFromTheSharedCachedEntityButRevalidationFetchesThemAgain() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getAutofillEntities(java.util.List.of("Q243"))).thenReturn(Map.of("Q243",
                mapper.readTree("{\"id\":\"Q243\",\"sitelinks\":{\"enwiki\":{\"title\":\"Eiffel Tower\"}}}")));
        when(wikidata.getWikipediaSitelinks("Q243")).thenReturn(Map.of("enwiki", "Eiffel Tower"));
        when(wikipedia.getPage("en", "Eiffel Tower", null)).thenReturn(page("Q243", "Eiffel Tower", "en",
                "Eiffel Tower text", "https://en.wikipedia.org/wiki/Eiffel_Tower"));
        WikipediaDescriptionService service = service(wikidata, wikipedia);

        var autofill = service.previewForAutofill("Q243");
        service.previewForAutofill("Q243");
        service.preview("Q243");

        assertThat(autofill.languages().get(1).status()).isEqualTo("AVAILABLE");
        org.mockito.Mockito.verify(wikidata, org.mockito.Mockito.times(1)).getAutofillEntities(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(wikidata, org.mockito.Mockito.times(1)).getWikipediaSitelinks("Q243");
        // 본문·판본은 캐시하지 않으므로 세 번 모두 새로 조회한다.
        org.mockito.Mockito.verify(wikipedia, org.mockito.Mockito.times(3)).getPage("en", "Eiffel Tower", null);
    }

    @Test
    void q243ChineseVariantsUseWikipediaConvertedTitlesAndNeverTheRawTitleOfAnotherVariant() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getWikipediaSitelinks("Q243")).thenReturn(Map.of("zhwiki", "艾菲爾鐵塔", "enwiki", "Eiffel Tower"));
        // 실제 Q243 zhwiki 응답: title 은 변형과 무관하게 艾菲爾鐵塔, 변형별 제목은 varianttitles 에만 있다.
        var zhCn = page("Q243", "艾菲爾鐵塔", "zh", "埃菲尔铁塔……", "https://zh.wikipedia.org/wiki/艾菲爾鐵塔");
        var zhTw = page("Q243", "艾菲爾鐵塔", "zh", "艾菲爾鐵塔……", "https://zh.wikipedia.org/wiki/艾菲爾鐵塔");
        for (var response : List.of(zhCn, zhTw)) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) response.path("query").path("pages").get(0))
                    .putObject("varianttitles").put("zh-cn", "埃菲尔铁塔").put("zh-tw", "艾菲爾鐵塔");
        }
        var noVariantTitles = page("Q243", "Eiffel Tower", "en", "text", "https://en.wikipedia.org/wiki/Eiffel_Tower");
        when(wikipedia.getPage("zh", "艾菲爾鐵塔", "zh-cn")).thenReturn(zhCn);
        when(wikipedia.getPage("zh", "艾菲爾鐵塔", "zh-tw")).thenReturn(zhTw);
        when(wikipedia.getPage("en", "Eiffel Tower", null)).thenReturn(noVariantTitles);

        var languages = service(wikidata, wikipedia).preview("Q243").languages();

        assertThat(languages.get(3).displayTitle()).isEqualTo("埃菲尔铁塔");
        assertThat(languages.get(4).displayTitle()).isEqualTo("艾菲爾鐵塔");
        // 출처 기록용 실제 문서 제목은 그대로 둔다.
        assertThat(languages.get(3).title()).isEqualTo("艾菲爾鐵塔");
        assertThat(languages.get(1).displayTitle()).isEqualTo("Eiffel Tower");
    }

    @Test
    void chineseVariantWithoutConvertedTitleLeavesDisplayTitleEmpty() throws Exception {
        WikidataApiClient wikidata = mock(WikidataApiClient.class);
        WikipediaApiClient wikipedia = mock(WikipediaApiClient.class);
        when(wikidata.getWikipediaSitelinks("Q1")).thenReturn(Map.of("zhwiki", "简体标题"));
        when(wikipedia.getPage(org.mockito.ArgumentMatchers.eq("zh"), org.mockito.ArgumentMatchers.eq("简体标题"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(page("Q1", "简体标题", "zh", "说明", "https://zh.wikipedia.org/wiki/简体标题"));

        var languages = service(wikidata, wikipedia).preview("Q1").languages();

        assertThat(languages.get(4).status()).isEqualTo("AVAILABLE");
        assertThat(languages.get(4).displayTitle()).isNull();
    }

    private WikipediaDescriptionService service(WikidataApiClient wikidata, WikipediaApiClient wikipedia) {
        return new WikipediaDescriptionService(wikidata, wikipedia, new WikidataAutofillCache(wikidata));
    }

    private com.fasterxml.jackson.databind.JsonNode page(String qid, String title, String language,
                                                          String extract, String url) throws Exception {
        var root = mapper.createObjectNode();
        var query = root.putObject("query");
        query.putObject("rightsinfo").put("text", "Creative Commons Attribution-Share Alike 4.0")
                .put("url", "https://creativecommons.org/licenses/by-sa/4.0/");
        var item = query.putArray("pages").addObject();
        item.put("title", title).put("pagelanguage", language).put("extract", extract).put("fullurl", url);
        item.put("lastrevid", 123L).putObject("pageprops").put("wikibase_item", qid);
        return root;
    }
}
