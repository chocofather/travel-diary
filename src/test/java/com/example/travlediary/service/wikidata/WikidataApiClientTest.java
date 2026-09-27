package com.example.travlediary.service.wikidata;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.hamcrest.Matchers.containsString;

class WikidataApiClientTest {

    @Test
    void searchesQidsAndFetchesExactLanguageFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbsearchentities"))
                .andExpect(queryParam("language", "ko"))
                .andExpect(queryParam("uselang", "ko"))
                .andRespond(withSuccess("{\"search\":[{\"id\":\"Q243\",\"display\":{"
                        + "\"label\":{\"value\":\"에펠탑\",\"language\":\"ko\"},"
                        + "\"description\":{\"value\":\"파리의 탑\",\"language\":\"ko\"}}},"
                        + "{\"id\":\"Q243\"},{\"id\":\"bad\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbgetentities"))
                .andExpect(queryParam("ids", "Q243"))
                .andExpect(queryParam("languages", "ko%7Cen%7Cja%7Czh%7Czh-hans%7Czh-cn%7Czh-hant%7Czh-tw"))
                .andRespond(withSuccess("{\"entities\":{\"Q243\":{\"labels\":{\"ko\":{\"value\":\"에펠탑\"}}}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchHits("에펠탑", "ko")).containsExactly(
                new WikidataApiClient.SearchHit("Q243", "에펠탑", "ko", "파리의 탑", "ko"));
        assertThat(client.getEntities(List.of("Q243"), false).get("Q243")
                .path("labels").path("ko").path("value").asText()).isEqualTo("에펠탑");
        server.verify();
    }

    @Test
    void rateLimitHasAnActionableError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.searchHits("Eiffel Tower", "en"))
                .isInstanceOf(WikidataApiException.class)
                .hasMessageContaining("잠시 후");
        server.verify();
    }

    @Test
    void tooManyRequestsWaitsForRetryAfterAndRetriesButForbiddenIsNotRetried() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<java.time.Duration> slept = new java.util.ArrayList<>();
        ExternalApiRateLimiter limiter = new ExternalApiRateLimiter(java.time.Clock.systemUTC(),
                slept::add, () -> 0.5, ExternalApiRateLimiter.MAX_ATTEMPTS);
        WikidataApiClient client = new WikidataApiClient(builder.build(), limiter);
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withTooManyRequests().header("Retry-After", "1"));
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{\"search\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        assertThat(client.searchHits("Eiffel Tower", "en")).isEmpty();
        assertThat(slept).hasSize(1);
        assertThat(slept.get(0)).isBetween(java.time.Duration.ofMillis(1), java.time.Duration.ofSeconds(1));
        // 403 은 요청 제한이 아니라 접근 거부라 다시 보내지 않는다(요청 1번만 기대).
        assertThatThrownBy(() -> client.searchHits("Eiffel Tower", "en"))
                .isInstanceOf(WikidataApiException.class)
                .isNotInstanceOf(WikidataRateLimitException.class)
                .hasMessageContaining("403");
        server.verify();
    }

    @Test
    void malformedApiResponseIsNotReportedAsAnEmptySearch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchHits("Eiffel Tower", "en"))
                .isInstanceOf(WikidataApiException.class)
                .hasMessageContaining("검색 응답");
        server.verify();
    }

    @Test
    void readsOnlySitelinksForTheRequestedQid() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbgetentities"))
                .andExpect(queryParam("ids", "Q243"))
                .andExpect(queryParam("props", "sitelinks"))
                .andRespond(withSuccess("{\"entities\":{\"Q243\":{\"sitelinks\":{\"enwiki\":{\"title\":\"Eiffel Tower\"},\"zhwiki\":{\"title\":\"艾菲爾鐵塔\"}}}}}", MediaType.APPLICATION_JSON));

        assertThat(client.getWikipediaSitelinks("Q243"))
                .isEqualTo(Map.of("enwiki", "Eiffel Tower", "zhwiki", "艾菲爾鐵塔"));
        server.verify();
    }

    @Test
    void photoCandidatesReadOnlyTheCommonsSitelinkAndRejectMissingItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("props", "sitelinks"))
                .andExpect(queryParam("sitefilter", "commonswiki"))
                .andRespond(withSuccess("{\"entities\":{\"Q243\":{\"sitelinks\":{\"commonswiki\":"
                        + "{\"title\":\"Category:Eiffel Tower\"}}}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{\"entities\":{\"Q404\":{\"missing\":\"\"}}}", MediaType.APPLICATION_JSON));

        assertThat(client.getCommonsSitelinkEntity("Q243").path("sitelinks").path("commonswiki").path("title").asText())
                .isEqualTo("Category:Eiffel Tower");
        assertThatThrownBy(() -> client.getCommonsSitelinkEntity("Q404"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        server.verify();
    }

    @Test
    void autofillEntityAndParentRegionClaimsUseOneSharedRequestShape() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbgetentities"))
                .andExpect(queryParam("ids", "Q243%7CQ90"))
                .andExpect(queryParam("props", "labels%7Cdescriptions%7Cclaims%7Csitelinks"))
                .andExpect(queryParam("sitefilter", "kowiki%7Cenwiki%7Cjawiki%7Czhwiki%7Ccommonswiki"))
                .andRespond(withSuccess("{\"entities\":{\"Q243\":{\"sitelinks\":{\"enwiki\":{\"title\":\"Eiffel Tower\"}}},"
                        + "\"Q90\":{\"missing\":\"\"}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbgetclaims"))
                .andExpect(queryParam("entity", "Q90"))
                .andExpect(queryParam("property", "P131"))
                .andRespond(withSuccess("{\"claims\":{\"P131\":[]}}", MediaType.APPLICATION_JSON));

        var entities = client.getAutofillEntities(List.of("Q243", "Q90"));

        assertThat(entities).containsOnlyKeys("Q243");
        assertThat(WikidataApiClient.wikipediaSitelinks(entities.get("Q243"))).containsEntry("enwiki", "Eiffel Tower");
        assertThat(client.getClaims("Q90", "P131").path("claims").has("P131")).isTrue();
        assertThatThrownBy(() -> client.getClaims("Q90", "P131|P17")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
