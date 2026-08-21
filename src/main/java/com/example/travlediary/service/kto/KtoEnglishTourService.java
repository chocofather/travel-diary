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

    public KtoEnglishTourDetailResponse getDetail(String contentId) {
        KtoTourApiResponse response = request("/detailCommon2",
                builder -> builder.queryParam("contentId", contentId));
        List<KtoTourApiResponse.Item> items = readItems(response.response().body().items());
        if (items.isEmpty()) {
            throw KtoEnglishTourApiException.upstreamFailure();
        }
        KtoTourApiResponse.Item item = items.get(0);
        return new KtoEnglishTourDetailResponse(
                KtoEnglishTitleMatcher.stripTrailingKoreanAlias(
                        KtoTourTextSanitizer.toPlainText(item.title())),
                KtoTourTextSanitizer.toPlainText(item.overview()));
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
