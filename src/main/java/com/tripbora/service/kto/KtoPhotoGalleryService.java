package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoPhotoGalleryApiResponse;
import com.tripbora.dto.kto.KtoPhotoSearchItemResponse;
import com.tripbora.dto.kto.KtoPhotoSearchResponse;
import com.tripbora.model.DestinationImageLicenseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class KtoPhotoGalleryService {

    public static final String SOURCE_TYPE = "KTO_PHOTO_GALLERY";
    public static final String SOURCE_NAME = "한국관광공사";
    /**
     * 공식 API 데이터셋 이용허락범위를 근거로 TYPE1 처리한다.
     *
     * <p>공공데이터포털 '한국관광공사_관광사진 정보_GW'(https://www.data.go.kr/data/15101914/openapi.do)
     * 설명: "포토코리아의 사진들은 공공누리 1유형의 콘텐츠들로서, 자유롭게 다운로드 및 활용이 가능합니다."
     * 이 API 응답에는 사진별 저작권 구분 필드가 없다. 사진별 유형을 추정한 값이 아니라
     * 데이터셋 단위 이용허락을 출처 정책으로 적용한 값이다. 워터마크 유무는 판정에 쓰지 않는다.
     * 데이터셋 이용허락이 바뀌면 이 값을 다시 확인해야 한다.</p>
     */
    public static final String DATASET_LICENSE_TYPE = "KOGL_TYPE_1";
    private static final String LICENSE_LABEL = DestinationImageLicenseType.displayName(DATASET_LICENSE_TYPE);
    /** 관광사진 API(포토코리아)가 주는 웹용 이미지 경로. TourAPI 이미지는 /cms/resource/ 를 쓴다. */
    private static final String GALLERY_IMAGE_HOST = "tong.visitkorea.or.kr";
    private static final String GALLERY_IMAGE_PATH_PREFIX = "/cms2/website/";

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
                    DATASET_LICENSE_TYPE,
                    LICENSE_LABEL
            ));
        } catch (Exception exception) {
            throw KtoPhotoApiException.upstreamFailure();
        }
    }

    /**
     * 관광사진 API 가 주는 이미지 주소인지 본다. 데이터셋 이용허락(TYPE1)은 이 주소의 사진에만 적용한다.
     * 화면이 보낸 출처 값을 믿지 않고 저장 시 서버가 주소로 출처를 나눌 때 쓴다.
     */
    public static boolean isGalleryImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(imageUrl.strip());
            return uri.getHost() != null
                    && GALLERY_IMAGE_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(GALLERY_IMAGE_PATH_PREFIX);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
