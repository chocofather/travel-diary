package com.example.travlediary.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class WikidataApiClient {

    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    private static final String LANGUAGES = "ko|en|ja|zh-hans|zh-hant";
    private final RestClient restClient;

    @Autowired
    public WikidataApiClient(RestClient.Builder builder,
                             @Value("${wikidata.api.base-url:https://www.wikidata.org}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "TripBoraWikidataPreview/1.0 (https://github.com/chocofather/travel-diary)")
                .build();
    }

    WikidataApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<String> searchIds(String keyword, String language) {
        JsonNode response = request(builder -> builder
                .queryParam("action", "wbsearchentities")
                .queryParam("search", keyword)
                .queryParam("language", language)
                .queryParam("type", "item")
                .queryParam("limit", 10));
        if (!response.path("search").isArray()) {
            throw new WikidataApiException("Wikidata 검색 응답을 해석하지 못했습니다.");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode item : response.path("search")) {
            String id = item.path("id").asText("");
            if (QID.matcher(id).matches() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public Map<String, JsonNode> getEntities(List<String> qids, boolean includeClaims) {
        if (qids == null || qids.isEmpty()) {
            return Map.of();
        }
        if (qids.stream().anyMatch(qid -> !QID.matcher(qid).matches())) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        Map<String, JsonNode> entities = new LinkedHashMap<>();
        for (int start = 0; start < qids.size(); start += 20) {
            List<String> batch = qids.subList(start, Math.min(start + 20, qids.size()));
            JsonNode response = request(builder -> builder
                    .queryParam("action", "wbgetentities")
                    .queryParam("ids", String.join("|", batch))
                    .queryParam("props", includeClaims ? "labels|descriptions|claims" : "labels|descriptions")
                    .queryParam("languages", LANGUAGES));
            if (!response.path("entities").isObject()) {
                throw new WikidataApiException("Wikidata 상세 응답을 해석하지 못했습니다.");
            }
            for (String qid : batch) {
                JsonNode entity = response.path("entities").path(qid);
                if (entity.isObject() && !entity.has("missing")) {
                    entities.put(qid, entity);
                }
            }
        }
        return entities;
    }

    private JsonNode request(java.util.function.Function<org.springframework.web.util.UriBuilder,
            org.springframework.web.util.UriBuilder> parameters) {
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> parameters.apply(builder.path("/w/api.php"))
                            .queryParam("format", "json")
                            .queryParam("formatversion", 2)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.has("error") || response.has("errors")) {
                throw new WikidataApiException("Wikidata 응답을 해석하지 못했습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new WikidataApiException("Wikidata 요청이 많습니다. 잠시 후 다시 시도해 주세요.", exception);
            }
            throw new WikidataApiException("Wikidata 정보를 불러오지 못했습니다.", exception);
        } catch (RestClientException exception) {
            throw new WikidataApiException("Wikidata에 연결하지 못했습니다.", exception);
        }
    }
}
