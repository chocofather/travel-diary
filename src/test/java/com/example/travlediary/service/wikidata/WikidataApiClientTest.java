package com.example.travlediary.service.wikidata;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

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
                .andRespond(withSuccess("{\"search\":[{\"id\":\"Q243\"},{\"id\":\"Q243\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "wbgetentities"))
                .andExpect(queryParam("ids", "Q243"))
                .andExpect(queryParam("languages", "ko%7Cen%7Cja%7Czh-hans%7Czh-hant"))
                .andRespond(withSuccess("{\"entities\":{\"Q243\":{\"labels\":{\"ko\":{\"value\":\"에펠탑\"}}}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchIds("에펠탑", "ko")).isEqualTo(List.of("Q243"));
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

        assertThatThrownBy(() -> client.searchIds("Eiffel Tower", "en"))
                .isInstanceOf(WikidataApiException.class)
                .hasMessageContaining("잠시 후");
        server.verify();
    }

    @Test
    void malformedApiResponseIsNotReportedAsAnEmptySearch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.wikidata.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikidataApiClient client = new WikidataApiClient(builder.build());
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchIds("Eiffel Tower", "en"))
                .isInstanceOf(WikidataApiException.class)
                .hasMessageContaining("검색 응답");
        server.verify();
    }
}
