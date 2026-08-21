package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourApiResponse;
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

@Service
public class KtoTourService {

    private static final Map<String, String> SUPPORTED_CONTENT_TYPES = Map.of(
            "12", "관광지",
            "14", "문화시설",
            "25", "여행코스",
            "28", "레포츠",
            "38", "쇼핑"
    );

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
        KtoTourApiResponse response = request("/searchKeyword2", builder -> builder
                .queryParam("keyword", keyword)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows));
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
                introFields.guide()
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
