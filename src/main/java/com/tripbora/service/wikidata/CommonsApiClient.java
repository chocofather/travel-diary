package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CommonsApiClient {
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    /** 라이선스 판별에 쓰는 파일 페이지 템플릿만 받는다(퍼블릭 도메인 근거·Licensed-PD의 사진 라이선스). */
    private static final String LICENSE_TEMPLATES = String.join("|", CommonsLicenseRules.REQUESTED_TEMPLATES);
    private static final Set<String> LICENSE_TEMPLATE_TITLES = Set.copyOf(CommonsLicenseRules.REQUESTED_TEMPLATES);
    /** 미리보기 한 묶음의 템플릿을 이어 받는 요청 수 상한. 파일 8개는 보통 1~2번이면 끝난다. */
    private static final int MAX_TEMPLATE_REQUESTS = 6;
    /** 미리보기에서 실제로 읽는 extmetadata 항목. 긴 설명·카테고리 목록을 빼 응답을 줄인다. 저장 재검증은 전체를 받는다. */
    private static final String PREVIEW_EXTMETADATA = "Artist|Credit|Source|Attribution|License|LicenseShortName"
            + "|LicenseUrl|AttributionRequired|Copyrighted|Restrictions";
    /** Commons categorymembers 이어받기 값(예: file|4c41...|198594051). */
    private static final java.util.regex.Pattern CONTINUE_TOKEN =
            java.util.regex.Pattern.compile("[A-Za-z0-9|]{1,1200}");
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExternalApiRateLimiter rateLimiter;

    @Autowired
    public CommonsApiClient(RestClient.Builder builder, ObjectMapper objectMapper, ExternalApiRateLimiter rateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl("https://commons.wikimedia.org")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "TripBoraCommonsPreview/1.0 (https://github.com/chocofather/tripbora)")
                .build();
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    CommonsApiClient(RestClient restClient, ObjectMapper objectMapper) {
        this(restClient, objectMapper, ExternalApiRateLimiter.singleAttempt());
    }

    CommonsApiClient(RestClient restClient, ObjectMapper objectMapper, ExternalApiRateLimiter rateLimiter) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    public List<String> listCategoryFiles(String category) {
        if (category == null || category.isBlank() || category.length() > 200
                || category.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("올바르지 않은 Commons 카테고리입니다.");
        }
        // 미리보기가 탐색하는 범위(카테고리 앞쪽 CATEGORY_SCAN_LIMIT개)와 같은 순서·범위를 후보로 인정한다.
        JsonNode response = request(builder -> builder
                .queryParam("list", "categorymembers")
                .queryParam("cmtitle", "Category:" + category)
                .queryParam("cmtype", "file")
                .queryParam("cmlimit", CommonsPhotoPreviewService.CATEGORY_SCAN_LIMIT));
        if (!response.path("query").path("categorymembers").isArray()) {
            throw new CommonsApiException("Commons 사진 목록 응답을 해석하지 못했습니다.");
        }
        List<String> titles = new ArrayList<>();
        for (JsonNode member : response.path("query").path("categorymembers")) {
            String title = member.path("title").asText("");
            if (member.path("ns").asInt(-1) == 6 && title.startsWith("File:")) {
                titles.add(title);
            }
        }
        return List.copyOf(titles);
    }

    /**
     * 미리보기용: 카테고리 파일 목록(순서 유지)과 각 파일의 240px 미리보기 메타데이터를 한 번에 받는다.
     * 응답의 query.categorymembers 는 {@link #listCategoryFiles}와 같은 순서이고, query.pages 에 imageinfo 가 있다.
     * 다음 묶음은 응답의 continue.cmcontinue 값을 continueToken 으로 넘겨 이어 받는다(목록과 파일 정보가 같이 이어진다).
     * 저장 전 재검증은 이 응답을 쓰지 않는다.
     */
    public JsonNode getCategoryImageInfo(String category, int limit, String continueToken) {
        if (category == null || category.isBlank() || category.length() > 200
                || category.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("올바르지 않은 Commons 카테고리입니다.");
        }
        if (limit < 1 || limit > 50
                || (continueToken != null && !CONTINUE_TOKEN.matcher(continueToken).matches())) {
            throw new IllegalArgumentException("올바르지 않은 Commons 사진 목록 요청입니다.");
        }
        JsonNode response = request(builder -> {
            builder.queryParam("list", "categorymembers")
                    .queryParam("cmtitle", "Category:" + category)
                    .queryParam("cmtype", "file")
                    .queryParam("cmlimit", limit)
                    .queryParam("generator", "categorymembers")
                    .queryParam("gcmtitle", "Category:" + category)
                    .queryParam("gcmtype", "file")
                    .queryParam("gcmlimit", limit)
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url|size|mime|extmetadata")
                    .queryParam("iiurlwidth", 240)
                    .queryParam("iiextmetadatalanguage", "en")
                    .queryParam("iiextmetadatafilter", PREVIEW_EXTMETADATA);
            if (continueToken != null) {
                builder.queryParam("cmcontinue", continueToken).queryParam("gcmcontinue", continueToken);
            }
            return builder;
        });
        if (!response.path("query").path("categorymembers").isArray()) {
            throw new CommonsApiException("Commons 사진 목록 응답을 해석하지 못했습니다.");
        }
        return withLicenseTemplates(response);
    }

    /**
     * QID가 없는 여행지의 수동 검색: File 네임스페이스에서 비트맵 파일만 검색해 각 파일의 240px 미리보기
     * 메타데이터를 함께 받는다. 결과 순서는 query.pages[].index(검색 순위)이고, 다음 묶음은
     * 응답의 continue.gsroffset 을 offset 으로 넘겨 이어 받는다. 결과가 없으면 응답에 query 가 없다.
     */
    public JsonNode searchImageInfo(String query, int limit, int offset) {
        if (query == null || query.isBlank() || query.length() > 200
                || query.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("올바르지 않은 Commons 검색어입니다.");
        }
        if (limit < 1 || limit > 50 || offset < 0 || offset >= CommonsPhotoPreviewService.MAX_SEARCH_OFFSET) {
            throw new IllegalArgumentException("올바르지 않은 Commons 검색 요청입니다.");
        }
        JsonNode response = request(builder -> {
            // filetype:bitmap 은 SVG·PDF·음성·영상을 검색 단계에서 뺀다. 최종 형식은 imageinfo 의 MIME 으로 다시 거른다.
            builder.queryParam("generator", "search")
                    .queryParam("gsrsearch", query + " filetype:bitmap")
                    .queryParam("gsrnamespace", 6)
                    .queryParam("gsrlimit", limit)
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url|size|mime|extmetadata")
                    .queryParam("iiurlwidth", 240)
                    .queryParam("iiextmetadatalanguage", "en")
                    .queryParam("iiextmetadatafilter", PREVIEW_EXTMETADATA);
            if (offset > 0) builder.queryParam("gsroffset", offset);
            return builder;
        });
        return withLicenseTemplates(response);
    }

    /**
     * 미리보기 묶음(검색·카테고리)의 파일들에 라이선스 판별 템플릿을 붙인다. 묶음 전체를 pageids 한 요청으로 묻고,
     * 템플릿이 많아 잘리면 tlcontinue 로 끝까지 이어 받는다(파일 수만큼 따로 묻지 않는다).
     *
     * <p>generator 나 여러 pageids 에 tltemplates 를 함께 걸면 Commons가 20초 넘게 걸리는 경우가 있어
     * (2026-10 'Eiffel Tower' 검색 확인, 같은 파일을 tltemplates 없이 물으면 1초 안팎) 이름 거르기는 여기서 한다.
     * 판별에 쓰는 템플릿만 남기므로 라이선스 판정에 들어가는 값은 tltemplates 로 받던 것과 같다.
     * 끝까지 받지 못하면 일부 템플릿만으로 판정하지 않도록 묶음을 실패로 돌린다.</p>
     */
    private JsonNode withLicenseTemplates(JsonNode response) {
        Map<Long, ArrayNode> templates = new LinkedHashMap<>();
        for (JsonNode page : response.path("query").path("pages")) {
            long pageId = page.path("pageid").asLong(0);
            if (pageId > 0) templates.put(pageId, objectMapper.createArrayNode());
        }
        if (templates.isEmpty()) return response;
        String pageIds = templates.keySet().stream().map(String::valueOf).collect(Collectors.joining("|"));
        String token = null;
        for (int requests = 0; ; requests++) {
            if (requests >= MAX_TEMPLATE_REQUESTS) {
                throw new CommonsApiException("Commons 라이선스 정보를 모두 받지 못했습니다. 다시 시도해 주세요.");
            }
            String continueToken = token;
            JsonNode part = request(builder -> {
                builder.queryParam("prop", "templates")
                        .queryParam("pageids", pageIds)
                        .queryParam("tlnamespace", 10)
                        .queryParam("tllimit", "max");
                if (continueToken != null) builder.queryParam("tlcontinue", continueToken);
                return builder;
            });
            for (JsonNode page : part.path("query").path("pages")) {
                ArrayNode target = templates.get(page.path("pageid").asLong(0));
                if (target == null) continue;
                for (JsonNode template : page.path("templates")) {
                    if (LICENSE_TEMPLATE_TITLES.contains(template.path("title").asText(""))) target.add(template);
                }
            }
            token = part.path("continue").path("tlcontinue").asText("");
            if (token.isEmpty()) break;
            if (token.length() > 600 || token.chars().anyMatch(Character::isISOControl)) {
                throw new CommonsApiException("Commons 라이선스 정보 응답을 해석하지 못했습니다.");
            }
        }
        ObjectNode merged = response.deepCopy();
        for (JsonNode page : merged.path("query").path("pages")) {
            ArrayNode pageTemplates = templates.get(page.path("pageid").asLong(0));
            if (pageTemplates != null && page instanceof ObjectNode node) node.set("templates", pageTemplates);
        }
        return merged;
    }

    public JsonNode getImageInfo(List<String> titles) {
        return imageInfo(titles, "imageinfo", 240, PREVIEW_EXTMETADATA);
    }

    /**
     * 저장 직전 재검증용 조회. 파일 페이지의 현재 판본 ID(info.lastrevid)를 라이선스 확인 근거로 함께 받고,
     * 내려받을 파일은 Commons가 만든 최대 1920px 폭 렌디션 URL(thumburl)로 받는다.
     */
    public JsonNode getImageInfoForSave(List<String> titles) {
        return imageInfo(titles, "imageinfo|info", CommonsPhotoPreviewService.SAVE_RENDITION_WIDTH, null);
    }

    private JsonNode imageInfo(List<String> titles, String properties, int thumbnailWidth, String metadataFilter) {
        if (titles == null || titles.isEmpty() || titles.size() > 9
                || titles.stream().anyMatch(title -> title == null || !title.startsWith("File:")
                || title.length() > 260 || title.indexOf('|') >= 0
                || title.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("올바르지 않은 Commons 파일 요청입니다.");
        }
        JsonNode response = request(builder -> {
            builder.queryParam("prop", properties + "|templates")
                    .queryParam("iiprop", "url|size|mime|extmetadata")
                    .queryParam("iiurlwidth", thumbnailWidth)
                    .queryParam("iiextmetadatalanguage", "en")
                    .queryParam("tltemplates", LICENSE_TEMPLATES)
                    .queryParam("tllimit", "max")
                    .queryParam("titles", String.join("|", titles));
            if (metadataFilter != null) builder.queryParam("iiextmetadatafilter", metadataFilter);
            return builder;
        });
        if (!response.path("query").path("pages").isArray()) {
            throw new CommonsApiException("Commons 사진 정보 응답을 해석하지 못했습니다.");
        }
        return response;
    }

    /** 요청 제한(429·503·ratelimited·maxlag)은 Commons 대기 상태를 지키며 한도 안에서 다시 시도한다. */
    private JsonNode request(Function<org.springframework.web.util.UriBuilder,
            org.springframework.web.util.UriBuilder> parameters) {
        return rateLimiter.call(ExternalApiRateLimiter.Service.COMMONS, () -> send(parameters),
                CommonsRateLimitException::new);
    }

    private JsonNode send(Function<org.springframework.web.util.UriBuilder,
            org.springframework.web.util.UriBuilder> parameters) {
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> parameters.apply(builder.path("/w/api.php"))
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("formatversion", 2)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, upstream) -> {
                        int status = upstream.getStatusCode().value();
                        if (status == 429 || status == 503) {
                            throw new ExternalApiRateLimiter.Limited(ExternalApiRateLimiter.parseRetryAfter(
                                    upstream.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
                        }
                        if (status == 403) {
                            throw new CommonsApiException(RateLimitMessages.forbidden("Commons"));
                        }
                        if (status < 200 || status >= 300) {
                            throw new CommonsApiException("Commons 사진 정보를 불러오지 못했습니다.");
                        }
                        if (upstream.getHeaders().getContentLength() > MAX_RESPONSE_BYTES) {
                            throw new CommonsApiException("Commons 응답이 너무 커서 사진을 표시하지 않았습니다.");
                        }
                        try {
                            byte[] body = upstream.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (body.length > MAX_RESPONSE_BYTES) {
                                throw new CommonsApiException("Commons 응답이 너무 커서 사진을 표시하지 않았습니다.");
                            }
                            return objectMapper.readTree(body);
                        } catch (IOException exception) {
                            throw new CommonsApiException("Commons 응답을 해석하지 못했습니다.", exception);
                        }
                    });
            String code = response == null ? "" : response.path("error").path("code").asText("");
            if ("ratelimited".equals(code) || "maxlag".equals(code)) {
                throw new ExternalApiRateLimiter.Limited(null);
            }
            if (response == null || response.has("error") || response.has("errors")) {
                throw new CommonsApiException("Commons 응답을 해석하지 못했습니다.");
            }
            return response;
        } catch (ResourceAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SocketTimeoutException) {
                throw new CommonsApiException("Commons 응답 시간이 초과되었습니다. 다시 시도해 주세요.", exception);
            }
            throw new CommonsApiException("Commons에 연결하지 못했습니다.", exception);
        } catch (RestClientException exception) {
            throw new CommonsApiException("Commons에 연결하지 못했습니다.", exception);
        }
    }
}
