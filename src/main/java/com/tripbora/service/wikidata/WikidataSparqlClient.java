package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Wikidata 공식 SPARQL 조회(WDQS). 해외 일괄 등록의 지역별 탐색이 행정구역 관계로 후보를 찾을 때만 쓴다.
 *
 * <p>WDQS는 요청마다 처리 시간이 길 수 있어 읽기 제한 시간을 따로 두고, 서버 전체에서 동시에 보내는 조회를
 * {@value #MAX_CONCURRENT_QUERIES}개로 묶는다. 결과는 호출하는 쪽이 캐시한다.</p>
 */
@Component
public class WikidataSparqlClient {
    static final int MAX_CONCURRENT_QUERIES = 2;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int WAIT_SECONDS = 20;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Semaphore queries = new Semaphore(MAX_CONCURRENT_QUERIES, true);
    private final ExternalApiRateLimiter rateLimiter;

    @Autowired
    public WikidataSparqlClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                ExternalApiRateLimiter rateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.restClient = builder.baseUrl("https://query.wikidata.org")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "TripBoraWikidataPreview/1.0 (https://github.com/chocofather/tripbora)")
                .build();
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    WikidataSparqlClient(RestClient restClient, ObjectMapper objectMapper) {
        this(restClient, objectMapper, ExternalApiRateLimiter.singleAttempt());
    }

    WikidataSparqlClient(RestClient restClient, ObjectMapper objectMapper, ExternalApiRateLimiter rateLimiter) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    /**
     * SELECT 결과의 results.bindings 배열을 돌려준다. WDQS의 429는 Wikidata API와 따로 대기 상태를 둔다
     * (지역 조회가 막혀도 여행지 등록의 Wikidata API 호출은 막지 않는다).
     */
    public JsonNode select(String query) {
        return rateLimiter.call(ExternalApiRateLimiter.Service.WIKIDATA_QUERY, () -> send(query),
                wait -> new WikidataRateLimitException(ExternalApiRateLimiter.Service.WIKIDATA_QUERY, wait));
    }

    private JsonNode send(String query) {
        boolean acquired;
        try {
            acquired = queries.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WikidataApiException("Wikidata 지역 조회가 중단되었습니다.");
        }
        if (!acquired) throw new WikidataApiException("Wikidata 지역 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> builder.path("/sparql").queryParam("query", "{query}").build(query))
                    .header(HttpHeaders.ACCEPT, "application/sparql-results+json")
                    .exchange((request, upstream) -> {
                        int status = upstream.getStatusCode().value();
                        if (status == 429) {
                            throw new ExternalApiRateLimiter.Limited(ExternalApiRateLimiter.parseRetryAfter(
                                    upstream.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
                        }
                        if (status == 403) {
                            throw new WikidataApiException(RateLimitMessages.forbidden("Wikidata 지역 조회"));
                        }
                        if (status == 500 || status == 502 || status == 503 || status == 504) {
                            throw new WikidataApiException("Wikidata 지역 조회 시간이 초과되었습니다. 더 좁은 지역으로 다시 시도해 주세요.");
                        }
                        if (status < 200 || status >= 300) {
                            throw new WikidataApiException("Wikidata 지역 정보를 불러오지 못했습니다.");
                        }
                        try {
                            byte[] body = upstream.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (body.length > MAX_RESPONSE_BYTES) {
                                throw new WikidataApiException("Wikidata 지역 조회 응답이 너무 큽니다.");
                            }
                            return objectMapper.readTree(body);
                        } catch (IOException exception) {
                            throw new WikidataApiException("Wikidata 지역 조회 응답을 해석하지 못했습니다.");
                        }
                    });
            JsonNode bindings = response == null ? null : response.path("results").path("bindings");
            if (bindings == null || !bindings.isArray()) {
                throw new WikidataApiException("Wikidata 지역 조회 응답을 해석하지 못했습니다.");
            }
            return bindings;
        } catch (ResourceAccessException exception) {
            throw new WikidataApiException(exception.getMostSpecificCause() instanceof SocketTimeoutException
                    ? "Wikidata 지역 조회 시간이 초과되었습니다. 더 좁은 지역으로 다시 시도해 주세요."
                    : "Wikidata에 연결하지 못했습니다.");
        } catch (RestClientException exception) {
            throw new WikidataApiException("Wikidata에 연결하지 못했습니다.");
        } finally {
            queries.release();
        }
    }
}
