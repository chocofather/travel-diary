package com.tripbora.service.pixabay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pixabay Image API 검색 클라이언트. API Key는 서버에서만 쓰고 화면·로그·예외 메시지에 내보내지 않는다.
 * 요청 제한(429)은 다시 시도하지 않고 바로 알린다.
 */
@Component
public class PixabayApiClient {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String USER_AGENT = "TripBoraPixabaySearch/1.0 (https://github.com/chocofather/tripbora)";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Clock clock;

    @Autowired
    public PixabayApiClient(RestClient.Builder builder, ObjectMapper objectMapper,
                            @Value("${pixabay.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl("https://pixabay.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.clock = Clock.systemUTC();
    }

    PixabayApiClient(RestClient restClient, ObjectMapper objectMapper, String apiKey, Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.clock = clock;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * 사진만(image_type=photo), 안전 검색(safesearch=true), 인기순(order=popular)으로 한 페이지를 받는다.
     * 검색어·키는 URI 변수로 넘겨 &·# 같은 문자가 다른 파라미터로 해석되지 않게 한다.
     */
    public PixabaySearchPage searchPhotos(String query, String language, int page, int perPage) {
        if (!isConfigured()) {
            throw PixabayApiException.notConfigured();
        }
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> builder.path("/api/")
                            .queryParam("key", "{key}")
                            .queryParam("q", "{q}")
                            .queryParam("lang", language)
                            .queryParam("image_type", "photo")
                            .queryParam("safesearch", "true")
                            .queryParam("order", "popular")
                            .queryParam("page", page)
                            .queryParam("per_page", perPage)
                            .build(Map.of("key", apiKey, "q", query)))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, upstream) -> {
                        int status = upstream.getStatusCode().value();
                        if (status == 429) {
                            throw PixabayApiException.rateLimited();
                        }
                        if (status < 200 || status >= 300) {
                            // 오류 본문(평문 설명)은 화면에 옮기지 않는다. 상태 코드만 알린다.
                            throw new PixabayApiException(PixabayApiException.Reason.UPSTREAM, status >= 500
                                    ? "Pixabay 서버 오류로 사진을 불러오지 못했습니다(HTTP " + status + "). 잠시 후 다시 시도해 주세요."
                                    : "Pixabay가 요청을 거절했습니다(HTTP " + status + "). API Key와 검색어를 확인해 주세요.");
                        }
                        if (upstream.getHeaders().getContentLength() > MAX_RESPONSE_BYTES) {
                            throw invalidResponse();
                        }
                        try {
                            byte[] body = upstream.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (body.length > MAX_RESPONSE_BYTES) {
                                throw invalidResponse();
                            }
                            return objectMapper.readTree(body);
                        } catch (IOException exception) {
                            throw invalidResponse();
                        }
                    });
            return parse(response);
        } catch (ResourceAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SocketTimeoutException) {
                throw new PixabayApiException(PixabayApiException.Reason.TIMEOUT,
                        "Pixabay 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new PixabayApiException(PixabayApiException.Reason.UPSTREAM, "Pixabay에 연결하지 못했습니다.");
        } catch (RestClientException exception) {
            throw new PixabayApiException(PixabayApiException.Reason.UPSTREAM, "Pixabay에 연결하지 못했습니다.");
        }
    }

    private PixabaySearchPage parse(JsonNode response) {
        if (response == null || !response.isObject() || !response.path("hits").isArray()) {
            throw invalidResponse();
        }
        List<PixabayHit> hits = new ArrayList<>();
        for (JsonNode hit : response.path("hits")) {
            PixabayHit parsed = parseHit(hit);
            if (parsed != null) {
                hits.add(parsed);
            }
        }
        return new PixabaySearchPage(Math.max(0, response.path("totalHits").asInt(0)), List.copyOf(hits),
                clock.instant());
    }

    /** id·원본 페이지가 없거나 사진이 아닌 항목은 건너뛴다. 저장할 수 있는 이미지 URL이 하나도 없어도 건너뛴다. */
    private PixabayHit parseHit(JsonNode hit) {
        long id = hit.path("id").canConvertToLong() ? hit.path("id").asLong() : 0;
        String pageUrl = text(hit, "pageURL");
        String type = text(hit, "type");
        if (id <= 0 || !isPixabayPage(pageUrl) || (type != null && !"photo".equals(type))) {
            return null;
        }
        PixabayHit parsed = new PixabayHit(id, pageUrl,
                text(hit, "previewURL"), text(hit, "webformatURL"), text(hit, "largeImageURL"),
                text(hit, "fullHDURL"), text(hit, "imageURL"),
                Math.max(0, hit.path("imageWidth").asInt(0)), Math.max(0, hit.path("imageHeight").asInt(0)),
                text(hit, "user"), text(hit, "tags"));
        return parsed.downloadCandidates().isEmpty() ? null : parsed;
    }

    private static boolean isPixabayPage(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && "pixabay.com".equals(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) return null;
        return value.asText().strip();
    }

    private static PixabayApiException invalidResponse() {
        return new PixabayApiException(PixabayApiException.Reason.UPSTREAM, "Pixabay 응답을 해석하지 못했습니다.");
    }
}
