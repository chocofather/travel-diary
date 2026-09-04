package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoEnglishTourCandidateResponse;
import com.example.travlediary.dto.kto.KtoEnglishTourDetailResponse;
import com.example.travlediary.dto.kto.KtoEnglishTourMatchResponse;
import com.example.travlediary.dto.kto.KtoTourApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class KtoEnglishTourService {

    private static final int SEARCH_RADIUS_METERS = 500;
    private static final int SEARCH_LIMIT = 20;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public KtoEnglishTourService(RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${kto.tour.eng-api-key:}") String apiKey,
                                 @Value("${kto.tour.eng-base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public KtoEnglishTourMatchResponse match(String koreanTitle, String mapX, String mapY) {
        String normalizedKoreanTitle = KtoEnglishTitleMatcher.normalizeName(koreanTitle);
        double sourceLongitude = parseCoordinate(mapX);
        double sourceLatitude = parseCoordinate(mapY);
        KtoTourApiResponse response = request("/locationBasedList2", builder -> builder
                .queryParam("mapX", mapX)
                .queryParam("mapY", mapY)
                .queryParam("radius", SEARCH_RADIUS_METERS)
                .queryParam("arrange", "E")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", SEARCH_LIMIT));

        List<KtoTourApiResponse.Item> exactAliasItems = readItems(response.response().body().items())
                .stream()
                .filter(item -> normalizedKoreanTitle != null
                        && normalizedKoreanTitle.equals(KtoEnglishTitleMatcher.extractKoreanAlias(
                        KtoTourTextSanitizer.toPlainText(item.title()))))
                .toList();
        if (exactAliasItems.size() != 1) {
            return KtoEnglishTourMatchResponse.noMatch();
        }
        KtoEnglishTourCandidateResponse matched = toCandidate(
                exactAliasItems.get(0), sourceLongitude, sourceLatitude);
        return matched == null
                ? KtoEnglishTourMatchResponse.noMatch()
                : KtoEnglishTourMatchResponse.matched(matched);
    }

    public KtoEnglishTourDetailResponse getDetail(String contentId, String contentTypeId) {
        KtoTourApiResponse response = request("/detailCommon2",
                builder -> builder.queryParam("contentId", contentId));
        List<KtoTourApiResponse.Item> items = readItems(response.response().body().items());
        if (items.isEmpty()) {
            throw KtoEnglishTourApiException.upstreamFailure();
        }
        KtoTourApiResponse.Item item = items.get(0);
        KtoTourApiResponse.Item intro = introItem(contentId, contentTypeId);
        return new KtoEnglishTourDetailResponse(
                KtoEnglishTitleMatcher.stripTrailingKoreanAlias(
                        KtoTourTextSanitizer.toPlainText(item.title())),
                KtoTourTextSanitizer.toPlainText(item.overview()),
                // 대표메뉴는 firstmenu 를 쓰고, 비어 있을 때만 treatmenu 로 대신한다.
                firstNonBlank(plainText(intro, KtoTourApiResponse.Item::firstmenu),
                        plainText(intro, KtoTourApiResponse.Item::treatmenu)),
                plainText(intro, KtoTourApiResponse.Item::opentimefood),
                plainText(intro, KtoTourApiResponse.Item::restdatefood));
    }

    /**
     * 유형별 상세(detailIntro2). 있으면 좋은 값이라 실패해도 예외를 올리지 않는다.
     *
     * <p>영문 유형 코드는 국문과 다르므로 매칭 결과로 받은 값을 그대로 쓴다. 값이 없으면 부르지 않는다.
     */
    private KtoTourApiResponse.Item introItem(String contentId, String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return null;
        }
        try {
            KtoTourApiResponse response = request("/detailIntro2", builder -> builder
                    .queryParam("contentId", contentId)
                    .queryParam("contentTypeId", contentTypeId));
            List<KtoTourApiResponse.Item> items = readItems(response.response().body().items());
            return items.isEmpty() ? null : items.get(0);
        } catch (RuntimeException exception) {
            // 상세가 없거나 조회에 실패해도 title/overview 자동입력은 그대로 둔다.
            return null;
        }
    }

    private String plainText(KtoTourApiResponse.Item intro,
                             java.util.function.Function<KtoTourApiResponse.Item, String> field) {
        return intro == null ? null : KtoTourTextSanitizer.toPlainText(field.apply(intro));
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private KtoEnglishTourCandidateResponse toCandidate(KtoTourApiResponse.Item item,
                                                        double sourceLongitude,
                                                        double sourceLatitude) {
        String contentId = normalize(item.contentid());
        String title = KtoTourTextSanitizer.toPlainText(item.title());
        Double longitude = parseCandidateCoordinate(item.mapx());
        Double latitude = parseCandidateCoordinate(item.mapy());
        if (contentId == null || title == null || longitude == null || latitude == null) {
            return null;
        }
        return new KtoEnglishTourCandidateResponse(
                contentId,
                normalize(item.contenttypeid()),
                title,
                normalize(item.mapx()),
                normalize(item.mapy()),
                distanceMeters(sourceLongitude, sourceLatitude, longitude, latitude));
    }

    private KtoTourApiResponse request(String path, Consumer<UriBuilder> specificParameters) {
        if (apiKey.isEmpty()) {
            throw KtoEnglishTourApiException.missingApiKey();
        }
        try {
            KtoTourApiResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        UriBuilder requestBuilder = uriBuilder
                                .path(path)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "TravelDiary")
                                .queryParam("_type", "json");
                        specificParameters.accept(requestBuilder);
                        URI requestUri = requestBuilder.build();
                        return UriComponentsBuilder.fromUri(requestUri)
                                .queryParam("serviceKey", apiKey)
                                .build(true)
                                .toUri();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (request, upstreamResponse) -> {
                                throw KtoEnglishTourApiException.upstreamFailure();
                            })
                    .body(KtoTourApiResponse.class);
            validate(response);
            return response;
        } catch (KtoEnglishTourApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw KtoEnglishTourApiException.upstreamFailure();
        }
    }

    private void validate(KtoTourApiResponse response) {
        if (response == null || response.response() == null
                || response.response().header() == null || response.response().body() == null
                || !"0000".equals(response.response().header().resultCode())) {
            throw KtoEnglishTourApiException.upstreamFailure();
        }
    }

    private List<KtoTourApiResponse.Item> readItems(JsonNode itemsNode) {
        if (itemsNode == null || !itemsNode.isObject()) {
            return List.of();
        }
        JsonNode itemNode = itemsNode.get("item");
        if (itemNode == null || itemNode.isNull() || itemNode.isTextual()) {
            return List.of();
        }
        List<KtoTourApiResponse.Item> items = new ArrayList<>();
        try {
            if (itemNode.isArray()) {
                for (JsonNode node : itemNode) {
                    items.add(objectMapper.treeToValue(node, KtoTourApiResponse.Item.class));
                }
            } else if (itemNode.isObject()) {
                items.add(objectMapper.treeToValue(itemNode, KtoTourApiResponse.Item.class));
            }
            return List.copyOf(items);
        } catch (Exception exception) {
            throw KtoEnglishTourApiException.upstreamFailure();
        }
    }

    private double distanceMeters(double longitude1, double latitude1,
                                  double longitude2, double latitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double latitude1Radians = Math.toRadians(latitude1);
        double latitude2Radians = Math.toRadians(latitude2);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(latitude1Radians) * Math.cos(latitude2Radians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double parseCoordinate(String value) {
        try {
            double coordinate = Double.parseDouble(value);
            if (!Double.isFinite(coordinate)) {
                throw new NumberFormatException();
            }
            return coordinate;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Coordinate must be finite", exception);
        }
    }

    private Double parseCandidateCoordinate(String value) {
        try {
            return parseCoordinate(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
