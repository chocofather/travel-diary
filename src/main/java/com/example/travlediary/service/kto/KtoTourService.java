package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourApiResponse;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.dto.kto.KtoTourSearchItemResponse;
import com.example.travlediary.dto.kto.KtoTourSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class KtoTourService {

    private static final Map<String, String> SUPPORTED_CONTENT_TYPES = Map.of(
            "12", "관광지",
            "14", "문화시설",
            "25", "여행코스",
            "28", "레포츠",
            "32", "숙박",
            "38", "쇼핑",
            "39", "음식점"
    );

    /**
     * 여행지 유형별 TourAPI 검색 범위.
     * 여기에 없는 유형은 기존처럼 contentTypeId 없이 전체 검색한다.
     */
    private static final String FOOD_CONTENT_TYPE_ID = "39";
    private static final String STAY_CONTENT_TYPE_ID = "32";

    private static final Map<String, String> SEARCH_CONTENT_TYPE_BY_DESTINATION_TYPE = Map.of(
            DestinationType.RESTAURANTS.name(), FOOD_CONTENT_TYPE_ID,
            DestinationType.CAFE.name(), FOOD_CONTENT_TYPE_ID,
            DestinationType.ACCOMMODATION.name(), STAY_CONTENT_TYPE_ID
    );

    /** 부정 표현이 긍정 표현을 포함하므로("불가능" ⊃ "가능") 부정을 먼저 검사한다. */
    private static final List<String> NEGATIVE_FLAG_WORDS = List.of("불가능", "불가", "없음");
    private static final List<String> POSITIVE_FLAG_WORDS = List.of("가능", "있음");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public KtoTourService(RestClient.Builder restClientBuilder,
                          ObjectMapper objectMapper,
                          @Value("${kto.tour.api-key:}") String apiKey,
                          @Value("${kto.tour.kor-base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public KtoTourSearchResponse search(String keyword, int pageNo, int numOfRows) {
        return search(keyword, pageNo, numOfRows, null);
    }

    /**
     * @param destinationType 관리자 폼에서 선택한 DestinationType 이름.
     *                        음식점/카페/숙소는 해당 TourAPI contentTypeId 로 검색 범위를 좁힌다.
     */
    public KtoTourSearchResponse search(String keyword, int pageNo, int numOfRows,
                                        String destinationType) {
        String searchContentTypeId = searchContentTypeId(destinationType);
        KtoTourApiResponse response = request("/searchKeyword2", builder -> {
            builder.queryParam("keyword", keyword)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows);
            if (searchContentTypeId != null) {
                builder.queryParam("contentTypeId", searchContentTypeId);
            }
        });
        KtoTourApiResponse.Body body = response.response().body();
        List<KtoTourSearchItemResponse> items = new ArrayList<>();
        for (KtoTourApiResponse.Item item : readItems(body.items())) {
            String typeName = SUPPORTED_CONTENT_TYPES.get(normalize(item.contenttypeid()));
            if (typeName == null) {
                continue;
            }
            items.add(new KtoTourSearchItemResponse(
                    normalize(item.contentid()),
                    normalize(item.contenttypeid()),
                    typeName,
                    normalize(item.title()),
                    join(item.addr1(), item.addr2()),
                    normalize(item.mapx()),
                    normalize(item.mapy())
            ));
        }
        return new KtoTourSearchResponse(
                valueOrDefault(body.pageNo(), pageNo),
                valueOrDefault(body.numOfRows(), numOfRows),
                valueOrDefault(body.totalCount(), 0),
                List.copyOf(items)
        );
    }

    private String searchContentTypeId(String destinationType) {
        if (destinationType == null || destinationType.isBlank()) {
            return null;
        }
        return SEARCH_CONTENT_TYPE_BY_DESTINATION_TYPE.get(destinationType.strip());
    }

    public KtoTourAutofillResponse getDetail(String contentId, String contentTypeId) {
        KtoTourApiResponse commonResponse = request("/detailCommon2", builder -> builder
                .queryParam("contentId", contentId));
        KtoTourApiResponse.Item common = firstItem(commonResponse.response().body().items());
        if (common == null) {
            throw KtoTourApiException.upstreamFailure();
        }

        KtoTourApiResponse introResponse = request("/detailIntro2", builder -> builder
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId));
        KtoTourApiResponse.Item intro = firstItem(introResponse.response().body().items());
        IntroFields introFields = introFields(contentTypeId, intro);

        return new KtoTourAutofillResponse(
                firstNonBlank(common.contentid(), contentId),
                firstNonBlank(common.contenttypeid(), contentTypeId),
                plainText(common.title()),
                join(common.addr1(), common.addr2()),
                normalize(common.mapx()),
                normalize(common.mapy()),
                plainText(common.overview()),
                homepageUrl(common.homepage()),
                firstNonBlank(plainText(common.tel()), introFields.informationCenter()),
                introFields.closedDays(),
                introFields.openingHours(),
                introFields.admissionFee(),
                introFields.guide(),
                // 유형별 추가 값. 원문을 그대로 보존하고 길이 제한은 두지 않는다.
                foodOnly(contentTypeId, intro, KtoTourApiResponse.Item::firstmenu),
                stayOnly(contentTypeId, intro, KtoTourApiResponse.Item::checkintime),
                stayOnly(contentTypeId, intro, KtoTourApiResponse.Item::checkouttime),
                leadingInt(stayOnly(contentTypeId, intro, KtoTourApiResponse.Item::roomcount)),
                stayOnly(contentTypeId, intro, KtoTourApiResponse.Item::roomtype),
                // 문자열 boolean 정보. 주차는 음식점(39)/숙박(32)이 서로 다른 필드명을 쓴다.
                firstNonNull(
                        booleanFlag(foodOnly(contentTypeId, intro, KtoTourApiResponse.Item::parkingfood)),
                        booleanFlag(stayOnly(contentTypeId, intro, KtoTourApiResponse.Item::parkinglodging))),
                booleanFlag(foodOnly(contentTypeId, intro, KtoTourApiResponse.Item::packing)),
                booleanFlag(foodOnly(contentTypeId, intro, KtoTourApiResponse.Item::reservationfood)),
                // 지역 매칭은 Controller 가 withRegionMatch 로 채운다.
                null
        );
    }

    private KtoTourApiResponse request(String path, Consumer<UriBuilder> specificParameters) {
        if (apiKey.isEmpty()) {
            throw KtoTourApiException.missingApiKey();
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
                            (request, upstreamResponse) -> { throw KtoTourApiException.upstreamFailure(); })
                    .body(KtoTourApiResponse.class);
            validate(response);
            return response;
        } catch (KtoTourApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private void validate(KtoTourApiResponse response) {
        if (response == null || response.response() == null
                || response.response().header() == null || response.response().body() == null
                || !"0000".equals(response.response().header().resultCode())) {
            throw KtoTourApiException.upstreamFailure();
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
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private String foodOnly(String contentTypeId, KtoTourApiResponse.Item intro,
                            Function<KtoTourApiResponse.Item, String> reader) {
        return typeOnly(FOOD_CONTENT_TYPE_ID, contentTypeId, intro, reader);
    }

    private String stayOnly(String contentTypeId, KtoTourApiResponse.Item intro,
                            Function<KtoTourApiResponse.Item, String> reader) {
        return typeOnly(STAY_CONTENT_TYPE_ID, contentTypeId, intro, reader);
    }

    private String typeOnly(String expectedContentTypeId, String contentTypeId,
                            KtoTourApiResponse.Item intro,
                            Function<KtoTourApiResponse.Item, String> reader) {
        if (intro == null || !expectedContentTypeId.equals(contentTypeId)) {
            return null;
        }
        return plainText(reader.apply(intro));
    }

    /**
     * "가능", "불가능", "0"/"1" 처럼 제각각인 TourAPI 문자열을 boolean 으로 읽는다.
     * 확실히 판별할 수 없으면 false 로 단정하지 않고 null 을 돌려준다.
     * "불가능" 안에 "가능" 이 들어 있으므로 부정 표현을 먼저 검사해야 한다.
     */
    private Boolean booleanFlag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if ("0".equals(normalized)) {
            return Boolean.FALSE;
        }
        if ("1".equals(normalized)) {
            return Boolean.TRUE;
        }
        for (String negative : NEGATIVE_FLAG_WORDS) {
            if (normalized.contains(negative)) {
                return Boolean.FALSE;
            }
        }
        for (String positive : POSITIVE_FLAG_WORDS) {
            if (normalized.contains(positive)) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    private Boolean firstNonNull(Boolean... values) {
        for (Boolean value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** "192실" 처럼 단위가 붙은 값에서 앞쪽 정수만 읽는다. 얻을 수 없으면 null. */
    private Integer leadingInt(String value) {
        if (value == null) {
            return null;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.valueOf(value.substring(0, end));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private KtoTourApiResponse.Item firstItem(JsonNode itemsNode) {
        List<KtoTourApiResponse.Item> items = readItems(itemsNode);
        return items.isEmpty() ? null : items.get(0);
    }

    private IntroFields introFields(String contentTypeId, KtoTourApiResponse.Item intro) {
        if (intro == null) {
            return IntroFields.empty();
        }
        return switch (contentTypeId) {
            case "12" -> new IntroFields(
                    plainText(intro.restdate()), plainText(intro.usetime()), plainText(intro.usefee()),
                    plainText(intro.infocenter()), plainText(intro.expguide()));
            case "14" -> new IntroFields(
                    plainText(intro.restdateculture()), plainText(intro.usetimeculture()), plainText(intro.usefee()),
                    plainText(intro.infocenterculture()), null);
            case "25" -> new IntroFields(
                    null, null, null, plainText(intro.infocentertourcourse()), plainText(intro.schedule()));
            case "28" -> new IntroFields(
                    plainText(intro.restdateleports()), plainText(intro.usetimeleports()),
                    plainText(intro.usefeeleports()), plainText(intro.infocenterleports()), null);
            case "38" -> new IntroFields(
                    plainText(intro.restdateshopping()), plainText(intro.opentime()), null,
                    plainText(intro.infocentershopping()), null);
            case FOOD_CONTENT_TYPE_ID -> new IntroFields(
                    plainText(intro.restdatefood()), plainText(intro.opentimefood()), null,
                    plainText(intro.infocenterfood()), null);
            case STAY_CONTENT_TYPE_ID -> new IntroFields(
                    null, null, null, plainText(intro.infocenterlodging()), null);
            default -> IntroFields.empty();
        };
    }

    private String homepageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(value);
        Element link = document.selectFirst("a[href]");
        String candidate = link == null ? document.text().strip() : link.attr("href").strip();
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String plainText(String value) {
        return KtoTourTextSanitizer.toPlainText(value);
    }

    private String join(String first, String second) {
        String normalizedFirst = plainText(first);
        String normalizedSecond = plainText(second);
        if (normalizedFirst == null) {
            return normalizedSecond;
        }
        if (normalizedSecond == null) {
            return normalizedFirst;
        }
        return normalizedFirst + " " + normalizedSecond;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record IntroFields(
            String closedDays,
            String openingHours,
            String admissionFee,
            String informationCenter,
            String guide
    ) {
        private static IntroFields empty() {
            return new IntroFields(null, null, null, null, null);
        }
    }
}
