package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoPhotoGalleryApiResponse;
import com.example.travlediary.dto.kto.KtoPhotoSearchItemResponse;
import com.example.travlediary.dto.kto.KtoPhotoSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class KtoPhotoGalleryService {

    private static final String SOURCE_TYPE = "KTO_PHOTO_GALLERY";
    private static final String SOURCE_NAME = "한국관광공사";
    private static final String LICENSE_TYPE = "KOGL_TYPE_1";
    private static final String LICENSE_LABEL = "공공누리 제1유형";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public KtoPhotoGalleryService(RestClient.Builder restClientBuilder,
                                  ObjectMapper objectMapper,
                                  @Value("${kto.photo.api-key:}") String apiKey,
                                  @Value("${kto.photo.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public KtoPhotoSearchResponse search(String keyword, int pageNo, int numOfRows) {
        if (apiKey.isEmpty()) {
            throw KtoPhotoApiException.missingApiKey();
        }

        KtoPhotoGalleryApiResponse apiResponse;
        try {
            apiResponse = restClient.get()
                    .uri(uriBuilder -> {
                        var requestUri = uriBuilder
                                .path("/gallerySearchList1")
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "TravelDiary")
                                .queryParam("keyword", keyword)
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows)
                                .queryParam("_type", "json")
                                .build();
                        return UriComponentsBuilder.fromUri(requestUri)
                                .queryParam("serviceKey", apiKey)
                                .build(true)
                                .toUri();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (request, response) -> { throw KtoPhotoApiException.upstreamFailure(); })
                    .body(KtoPhotoGalleryApiResponse.class);
        } catch (KtoPhotoApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw KtoPhotoApiException.upstreamFailure();
        }

        if (apiResponse == null || apiResponse.response() == null
                || apiResponse.response().header() == null || apiResponse.response().body() == null
                || !"0000".equals(apiResponse.response().header().resultCode())) {
            throw KtoPhotoApiException.upstreamFailure();
        }

        KtoPhotoGalleryApiResponse.Body body = apiResponse.response().body();
        return new KtoPhotoSearchResponse(
                valueOrDefault(body.pageNo(), pageNo),
                valueOrDefault(body.numOfRows(), numOfRows),
                valueOrDefault(body.totalCount(), 0),
                items(body.items())
        );
    }

    private List<KtoPhotoSearchItemResponse> items(JsonNode itemsNode) {
        if (itemsNode == null || itemsNode.isNull() || !itemsNode.isObject()) {
            return List.of();
        }
        JsonNode itemNode = itemsNode.get("item");
        if (itemNode == null || itemNode.isNull() || itemNode.isTextual()) {
            return List.of();
        }

        List<KtoPhotoSearchItemResponse> items = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                addItem(items, node);
            }
        } else if (itemNode.isObject()) {
            addItem(items, itemNode);
        }
        return List.copyOf(items);
    }

    private void addItem(List<KtoPhotoSearchItemResponse> target, JsonNode itemNode) {
        try {
            KtoPhotoGalleryApiResponse.Item item = objectMapper.treeToValue(
                    itemNode, KtoPhotoGalleryApiResponse.Item.class);
            target.add(new KtoPhotoSearchItemResponse(
                    item.galContentId(),
                    item.galTitle(),
                    item.galWebImageUrl(),
                    item.galPhotographyMonth(),
                    item.galPhotographyLocation(),
                    item.galPhotographer(),
                    item.galSearchKeyword(),
                    item.galCreatedtime(),
                    item.galModifiedtime(),
                    SOURCE_TYPE,
                    SOURCE_NAME,
                    LICENSE_TYPE,
                    LICENSE_LABEL
            ));
        } catch (Exception exception) {
            throw KtoPhotoApiException.upstreamFailure();
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
