package com.tripbora.service.wikidata;

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
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@Component
public class WikidataApiClient {

    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    /** zh(표기 미지정)·zh-cn 설명은 간단 설명 보완에만 쓴다. 제목은 zh-hans·zh-tw·zh-hant 만 쓴다. */
    private static final String LANGUAGES = "ko|en|ja|zh|zh-hans|zh-cn|zh-hant|zh-tw";
    private static final String AUTOFILL_SITES = "kowiki|enwiki|jawiki|zhwiki|commonswiki";
    private final RestClient restClient;
    private final ExternalApiRateLimiter rateLimiter;

    @Autowired
    public WikidataApiClient(RestClient.Builder builder,
                             @Value("${wikidata.api.base-url:https://www.wikidata.org}") String baseUrl,
                             ExternalApiRateLimiter rateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "TripBoraWikidataPreview/1.0 (https://github.com/chocofather/tripbora)")
                .build();
        this.rateLimiter = rateLimiter;
    }

    WikidataApiClient(RestClient restClient) {
        this(restClient, ExternalApiRateLimiter.singleAttempt());
    }

    WikidataApiClient(RestClient restClient, ExternalApiRateLimiter rateLimiter) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
    }

    /** 검색 결과 목록용 최소 정보. 관리자 화면 언어로 표시 라벨·설명을 받는다. */
    public List<SearchHit> searchHits(String keyword, String language) {
        return searchPage(keyword, language, 0).hits();
    }

    /**
     * 검색 결과 한 페이지(10건). offset 은 앞 페이지 응답의 search-continue 값이며, 더 없으면 nextOffset 이 null 이다.
     */
    public SearchPage searchPage(String keyword, String language, int offset) {
        if (offset < 0 || offset > MAX_SEARCH_OFFSET) {
            throw new IllegalArgumentException("검색 결과 위치가 올바르지 않습니다.");
        }
        JsonNode response = request(builder -> builder
                .queryParam("action", "wbsearchentities")
                .queryParam("search", keyword)
                .queryParam("language", language)
                .queryParam("uselang", language)
                .queryParam("type", "item")
                .queryParam("limit", 10)
                .queryParam("continue", offset));
        if (!response.path("search").isArray()) {
            throw new WikidataApiException("Wikidata 검색 응답을 해석하지 못했습니다.");
        }
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode item : response.path("search")) {
            String id = item.path("id").asText("");
            if (!QID.matcher(id).matches() || hits.stream().anyMatch(hit -> hit.qid().equals(id))) {
                continue;
            }
            JsonNode label = item.path("display").path("label");
            JsonNode description = item.path("display").path("description");
            hits.add(new SearchHit(id, text(label.path("value")), text(label.path("language")),
                    text(description.path("value")), text(description.path("language"))));
        }
        JsonNode next = response.path("search-continue");
        Integer nextOffset = next.canConvertToInt() && next.asInt() > offset && next.asInt() <= MAX_SEARCH_OFFSET
                ? next.asInt() : null;
        return new SearchPage(List.copyOf(hits), nextOffset);
    }

    /** Wikidata 검색은 앞쪽 결과가 가장 관련 있으므로 이어 보기는 이 위치까지만 허용한다. */
    static final int MAX_SEARCH_OFFSET = 200;

    public record SearchHit(String qid, String label, String labelLanguage,
                            String description, String descriptionLanguage) {
    }

    public record SearchPage(List<SearchHit> hits, Integer nextOffset) {
    }

    /**
     * 자동입력 화면이 한 QID에 필요한 값을 한 번에 받는다.
     * 기본정보(라벨·설명·claims), Wikipedia 문서 연결, Commons 카테고리 연결을 같은 응답으로 공유한다.
     */
    public Map<String, JsonNode> getAutofillEntities(List<String> qids) {
        return entities(qids, "labels|descriptions|claims|sitelinks", AUTOFILL_SITES);
    }

    /** 한 속성의 claims만 받는다. 상위 지역 탐색에서 전체 claims 대신 P131만 필요할 때 쓴다. */
    public JsonNode getClaims(String qid, String property) {
        if (qid == null || !QID.matcher(qid).matches() || property == null || !property.matches("P[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata 요청입니다.");
        }
        JsonNode response = request(builder -> builder
                .queryParam("action", "wbgetclaims")
                .queryParam("entity", qid)
                .queryParam("property", property));
        if (!response.path("claims").isObject()) {
            throw new WikidataApiException("Wikidata 지역 응답을 해석하지 못했습니다.");
        }
        return response;
    }

    /** Wikipedia 4개 사이트 연결을 엔티티에서 읽는다. 저장 전 재검증과 자동입력이 같은 규칙을 쓴다. */
    public static Map<String, String> wikipediaSitelinks(JsonNode entity) {
        if (!entity.path("sitelinks").isObject() && !entity.path("sitelinks").isMissingNode()) {
            throw new WikidataApiException("Wikidata Wikipedia 연결 정보를 해석하지 못했습니다.");
        }
        Map<String, String> sitelinks = new LinkedHashMap<>();
        for (String site : List.of("kowiki", "enwiki", "jawiki", "zhwiki")) {
            String title = entity.path("sitelinks").path(site).path("title").asText("");
            if (!title.isBlank()) sitelinks.put(site, title);
        }
        return sitelinks;
    }

    public Map<String, JsonNode> getEntities(List<String> qids, boolean includeClaims) {
        return entities(qids, includeClaims ? "labels|descriptions|claims" : "labels|descriptions", null);
    }

    private Map<String, JsonNode> entities(List<String> qids, String props, String sites) {
        if (qids == null || qids.isEmpty()) {
            return Map.of();
        }
        if (qids.stream().anyMatch(qid -> qid == null || !QID.matcher(qid).matches())) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        Map<String, JsonNode> entities = new LinkedHashMap<>();
        for (int start = 0; start < qids.size(); start += 20) {
            List<String> batch = qids.subList(start, Math.min(start + 20, qids.size()));
            JsonNode response = request(builder -> {
                builder.queryParam("action", "wbgetentities")
                        .queryParam("ids", String.join("|", batch))
                        .queryParam("props", props)
                        .queryParam("languages", LANGUAGES);
                return sites == null ? builder : builder.queryParam("sitefilter", sites);
            });
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

    public Map<String, String> getWikipediaSitelinks(String qid) {
        if (qid == null || !QID.matcher(qid).matches()) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        JsonNode response = request(builder -> builder
                .queryParam("action", "wbgetentities")
                .queryParam("ids", qid)
                .queryParam("props", "sitelinks")
                .queryParam("sitefilter", "kowiki|enwiki|jawiki|zhwiki"));
        JsonNode entity = response.path("entities").path(qid);
        if (entity.has("missing") || entity.isMissingNode()) {
            throw new NoSuchElementException("해당 Wikidata 후보를 찾을 수 없습니다.");
        }
        return wikipediaSitelinks(entity);
    }

    /** Commons 카테고리 연결(commonswiki sitelink)만 받는다. 없는 항목이면 NoSuchElementException. */
    public JsonNode getCommonsSitelinkEntity(String qid) {
        if (qid == null || !QID.matcher(qid).matches()) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        JsonNode response = request(builder -> builder
                .queryParam("action", "wbgetentities")
                .queryParam("ids", qid)
                .queryParam("props", "sitelinks")
                .queryParam("sitefilter", "commonswiki"));
        JsonNode entity = response.path("entities").path(qid);
        if (!entity.isObject() || entity.has("missing")) {
            throw new NoSuchElementException("해당 Wikidata 후보를 찾을 수 없습니다.");
        }
        return entity;
    }

    private static String text(JsonNode node) {
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /** 요청 제한(429·503·ratelimited·maxlag)은 서버 전체가 공유하는 대기 상태를 지키며 한도 안에서 다시 시도한다. */
    private JsonNode request(java.util.function.Function<org.springframework.web.util.UriBuilder,
            org.springframework.web.util.UriBuilder> parameters) {
        return rateLimiter.call(ExternalApiRateLimiter.Service.WIKIDATA, () -> send(parameters),
                wait -> new WikidataRateLimitException(ExternalApiRateLimiter.Service.WIKIDATA, wait));
    }

    private JsonNode send(java.util.function.Function<org.springframework.web.util.UriBuilder,
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
            String code = response == null ? "" : response.path("error").path("code").asText("");
            if ("ratelimited".equals(code) || "maxlag".equals(code)) {
                throw new ExternalApiRateLimiter.Limited(null);
            }
            if (response == null || response.has("error") || response.has("errors")) {
                throw new WikidataApiException("Wikidata 응답을 해석하지 못했습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429 || status == 503) {
                throw new ExternalApiRateLimiter.Limited(ExternalApiRateLimiter.parseRetryAfter(
                        exception.getResponseHeaders() == null ? null
                                : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
            }
            if (status == 403) {
                throw new WikidataApiException(RateLimitMessages.forbidden("Wikidata"), exception);
            }
            throw new WikidataApiException("Wikidata 정보를 불러오지 못했습니다.", exception);
        } catch (RestClientException exception) {
            throw new WikidataApiException("Wikidata에 연결하지 못했습니다.", exception);
        }
    }
}
