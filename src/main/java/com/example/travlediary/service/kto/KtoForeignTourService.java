package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoForeignTourCandidateResponse;
import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * TourAPI 외국어 서비스(Eng/Jpn/Chs/Cht) 공통 호출.
 *
 * <p>언어마다 base URL 만 다르고 인증키·요청 형식·응답 구조는 같다.
 * 국문에서 옮겨 온 값을 그대로 쓰지 않도록, 유형 코드는 국문과 매핑하지 않고
 * 매칭된 항목이 알려 준 {@code contenttypeid} 를 detailIntro2 에 그대로 넘긴다.
 * (같은 관광지가 언어에 따라 76 또는 78 로 갈린다)
 */
@Service
public class KtoForeignTourService {

    /**
     * TourAPI 외국어 서비스의 유형 코드. 국문 코드(12/14/32/38/39)와 값이 다르며,
     * 매칭 결과가 알려 준 값을 그대로 비교하기 위한 상수일 뿐 국문에서 변환하지 않는다.
     */
    private static final String LEISURE_SPORTS = "75";
    private static final String TOURIST_ATTRACTION = "76";
    private static final String CULTURAL_FACILITY = "78";
    private static final String SHOPPING = "79";
    private static final String LODGING = "80";
    private static final String RESTAURANT = "82";
    /**
     * 축제·행사. 외국어 locationBasedList2 는 이 유형을 돌려주지 않는다(실측 확인).
     * 그래서 축제만 국문 제목 키워드 검색으로 따로 찾는다.
     */
    private static final String FESTIVAL = "85";

    private static final int SEARCH_RADIUS_METERS = 500;
    private static final int SEARCH_LIMIT = 20;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final Map<KtoForeignLanguage, RestClient> restClients =
            new EnumMap<>(KtoForeignLanguage.class);
    private final ObjectMapper objectMapper;
    /** 국문·외국어 다섯 서비스가 같은 공공데이터포털 인증키를 쓴다. */
    private final String apiKey;

    public KtoForeignTourService(RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${kto.tour.api-key:}") String apiKey,
                                 @Value("${kto.tour.eng-base-url}") String englishBaseUrl,
                                 @Value("${kto.tour.jpn-base-url}") String japaneseBaseUrl,
                                 @Value("${kto.tour.chs-base-url}") String simplifiedBaseUrl,
                                 @Value("${kto.tour.cht-base-url}") String traditionalBaseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        restClients.put(KtoForeignLanguage.ENGLISH,
                restClientBuilder.clone().baseUrl(englishBaseUrl).build());
        restClients.put(KtoForeignLanguage.JAPANESE,
                restClientBuilder.clone().baseUrl(japaneseBaseUrl).build());
        restClients.put(KtoForeignLanguage.CHINESE_SIMPLIFIED,
                restClientBuilder.clone().baseUrl(simplifiedBaseUrl).build());
        restClients.put(KtoForeignLanguage.CHINESE_TRADITIONAL,
                restClientBuilder.clone().baseUrl(traditionalBaseUrl).build());
    }

    /**
     * 좌표 주변에서 같은 장소를 찾는다.
     *
     * <p>제목 끝의 한글 별칭이 국문 여행지명과 정확히 같은 후보가 하나일 때만 매칭으로 본다.
     * 거리는 화면 표시용이며 판단 근거로 쓰지 않는다.
     */
    public KtoForeignTourMatchResponse match(KtoForeignLanguage language,
                                             String koreanTitle, String mapX, String mapY) {
        return match(language, koreanTitle, mapX, mapY, false);
    }

    /**
     * 좌표 주변에서 같은 장소를 찾는다.
     *
     * <p>{@code includeFestival} 은 축제·행사 화면에서만 켠다. 외국어 locationBasedList2 가
     * 축제(85)를 돌려주지 않아, 켠 경우에만 국문 제목 키워드 검색을 한 번 더 한다.
     * 여행지 화면은 꺼진 채로 부르므로 호출 수와 결과가 예전과 같다.
     */
    public KtoForeignTourMatchResponse match(KtoForeignLanguage language,
                                             String koreanTitle, String mapX, String mapY,
                                             boolean includeFestival) {
        String normalizedKoreanTitle = KtoKoreanAliasMatcher.normalizeName(koreanTitle);
        double sourceLongitude = parseCoordinate(mapX);
        double sourceLatitude = parseCoordinate(mapY);
        KtoTourApiResponse response = request(language, "/locationBasedList2", builder -> builder
                .queryParam("mapX", mapX)
                .queryParam("mapY", mapY)
                .queryParam("radius", SEARCH_RADIUS_METERS)
                .queryParam("arrange", "E")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", SEARCH_LIMIT));

        List<KtoTourApiResponse.Item> exactAliasItems = exactAliasItems(
                readItems(language, response.response().body().items()), normalizedKoreanTitle);
        if (includeFestival && exactAliasItems.isEmpty()) {
            // 좌표 목록에 없는 유형(축제·행사)만 국문 제목으로 한 번 더 찾는다.
            exactAliasItems = festivalItems(language, koreanTitle, normalizedKoreanTitle,
                    sourceLongitude, sourceLatitude);
        }
        if (exactAliasItems.size() != 1) {
            return KtoForeignTourMatchResponse.noMatch();
        }
        KtoForeignTourCandidateResponse matched = toCandidate(
                exactAliasItems.get(0), sourceLongitude, sourceLatitude);
        return matched == null
                ? KtoForeignTourMatchResponse.noMatch()
                : KtoForeignTourMatchResponse.matched(matched);
    }

    /** 제목 끝 한글 별칭이 국문 이름과 정확히 같은 항목만 남긴다. */
    private List<KtoTourApiResponse.Item> exactAliasItems(List<KtoTourApiResponse.Item> items,
                                                          String normalizedKoreanTitle) {
        return items.stream()
                .filter(item -> normalizedKoreanTitle != null
                        && normalizedKoreanTitle.equals(KtoKoreanAliasMatcher.extractKoreanAlias(
                        KtoTourTextSanitizer.toPlainText(item.title()))))
                .toList();
    }

    /**
     * 축제·행사 후보. 외국어 locationBasedList2 가 85 를 돌려주지 않아 좌표로는 찾을 수 없다.
     *
     * <p>외국어 제목이 끝에 국문 이름을 그대로 달고 있어 국문 제목으로 키워드 검색을 하면 걸린다.
     * 판단 기준은 다른 유형과 같다 — 한글 별칭 정확 일치, 후보 하나, 좌표 반경 안.
     * 조회가 실패하면 매칭 없음으로 두고 기존 결과를 막지 않는다.
     */
    private List<KtoTourApiResponse.Item> festivalItems(KtoForeignLanguage language,
                                                        String koreanTitle,
                                                        String normalizedKoreanTitle,
                                                        double sourceLongitude,
                                                        double sourceLatitude) {
        try {
            KtoTourApiResponse response = request(language, "/searchKeyword2", builder -> builder
                    .queryParam("keyword", koreanTitle)
                    .queryParam("arrange", "A")
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", SEARCH_LIMIT));
            return exactAliasItems(
                    readItems(language, response.response().body().items()), normalizedKoreanTitle)
                    .stream()
                    .filter(item -> FESTIVAL.equals(normalize(item.contenttypeid())))
                    .filter(item -> withinSearchRadius(item, sourceLongitude, sourceLatitude))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** 키워드 검색은 전국을 훑으므로 좌표 반경으로 한 번 더 거른다. */
    private boolean withinSearchRadius(KtoTourApiResponse.Item item,
                                       double sourceLongitude, double sourceLatitude) {
        Double longitude = parseCandidateCoordinate(item.mapx());
        Double latitude = parseCandidateCoordinate(item.mapy());
        return longitude != null && latitude != null
                && distanceMeters(sourceLongitude, sourceLatitude, longitude, latitude)
                <= SEARCH_RADIUS_METERS;
    }

    /**
     * 매칭된 항목의 상세. contentTypeId 는 매칭 결과에서 받은 외국어 유형 코드를 그대로 넘긴다.
     */
    public KtoForeignTourDetailResponse getDetail(KtoForeignLanguage language,
                                                  String contentId, String contentTypeId) {
        KtoTourApiResponse response = request(language, "/detailCommon2",
                builder -> builder.queryParam("contentId", contentId));
        List<KtoTourApiResponse.Item> items =
                readItems(language, response.response().body().items());
        if (items.isEmpty()) {
            throw KtoForeignTourApiException.upstreamFailure(language);
        }
        KtoTourApiResponse.Item item = items.get(0);
        KtoTourApiResponse.Item intro = introItem(language, contentId, contentTypeId);
        // 유형 코드가 없으면 어느 분기에도 걸리지 않도록 빈 문자열로 둔다.
        String foreignTypeId = contentTypeId == null ? "" : contentTypeId.strip();
        return new KtoForeignTourDetailResponse(
                KtoKoreanAliasMatcher.stripTrailingKoreanAlias(
                        KtoTourTextSanitizer.toPlainText(item.title())),
                KtoTourTextSanitizer.toPlainText(item.overview()),
                closedDaysOf(intro, foreignTypeId),
                openingHoursOf(intro, foreignTypeId),
                admissionFeeOf(intro, foreignTypeId),
                mainMenuOf(intro, foreignTypeId),
                roomTypeOf(intro, foreignTypeId),
                mainProductsOf(intro, foreignTypeId),
                festivalText(intro, foreignTypeId, KtoTourApiResponse.Item::eventplace),
                festivalAddressOf(item, foreignTypeId),
                festivalText(intro, foreignTypeId, KtoTourApiResponse.Item::playtime),
                festivalText(intro, foreignTypeId, KtoTourApiResponse.Item::usetimefestival),
                festivalText(intro, foreignTypeId, KtoTourApiResponse.Item::sponsor1),
                festivalText(intro, foreignTypeId, KtoTourApiResponse.Item::sponsor2));
    }

    /**
     * 축제·행사(85) detailIntro2 값. 네 언어가 모두 같은 필드 이름을 쓴다(실측 확인).
     *
     * <p>eventplace / playtime / usetimefestival / sponsor1 / sponsor2 만 옮긴다.
     * 연락처(sponsor1tel·sponsor2tel)·홈페이지는 언어와 무관해 번역 대상이 아니고,
     * placeinfo·program·subevent 처럼 대응하는 칸이 없는 값은 가져오지 않는다.
     */
    private String festivalText(KtoTourApiResponse.Item intro, String foreignTypeId,
                                Function<KtoTourApiResponse.Item, String> field) {
        return FESTIVAL.equals(foreignTypeId) ? plainText(intro, field) : null;
    }

    /**
     * 축제·행사 주소. detailIntro2 에는 주소 필드가 없어 detailCommon2 의 addr1/addr2 를 쓴다.
     * 두 값 모두 요청한 언어로 내려온다.
     */
    private String festivalAddressOf(KtoTourApiResponse.Item common, String foreignTypeId) {
        if (!FESTIVAL.equals(foreignTypeId)) {
            return null;
        }
        String primary = KtoTourTextSanitizer.toPlainText(common.addr1());
        String secondary = KtoTourTextSanitizer.toPlainText(common.addr2());
        if (primary == null) {
            return secondary;
        }
        return secondary == null ? primary : primary + " " + secondary;
    }

    /*
     * 유형별 상세는 같은 뜻이라도 유형마다 필드 이름이 다르다.
     * 여기서 보는 코드는 매칭 결과가 알려 준 외국어 유형 값이며, 국문 코드에서 바꾼 값이 아니다.
     * 대응이 분명한 칸만 옮기고 나머지는 비워 둔다.
     */

    /** 관광지(76) / 문화시설(78) / 음식점·카페(82) / 쇼핑(79) 휴무일. */
    private String closedDaysOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        return switch (foreignTypeId) {
            case TOURIST_ATTRACTION -> plainText(intro, KtoTourApiResponse.Item::restdate);
            case CULTURAL_FACILITY -> plainText(intro, KtoTourApiResponse.Item::restdateculture);
            case RESTAURANT -> plainText(intro, KtoTourApiResponse.Item::restdatefood);
            case SHOPPING -> plainText(intro, KtoTourApiResponse.Item::restdateshopping);
            default -> null;
        };
    }

    /** 관광지(76) / 문화시설(78) / 음식점·카페(82) / 체험(75) / 쇼핑(79) 운영시간. */
    private String openingHoursOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        return switch (foreignTypeId) {
            case TOURIST_ATTRACTION -> plainText(intro, KtoTourApiResponse.Item::usetime);
            case CULTURAL_FACILITY -> plainText(intro, KtoTourApiResponse.Item::usetimeculture);
            case RESTAURANT -> plainText(intro, KtoTourApiResponse.Item::opentimefood);
            case LEISURE_SPORTS -> plainText(intro, KtoTourApiResponse.Item::usetimeleports);
            case SHOPPING -> plainText(intro, KtoTourApiResponse.Item::opentime);
            default -> null;
        };
    }

    /** 관광지·문화시설은 usefee, 체험은 usefeeleports 를 쓴다. */
    private String admissionFeeOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        return switch (foreignTypeId) {
            case TOURIST_ATTRACTION, CULTURAL_FACILITY ->
                    plainText(intro, KtoTourApiResponse.Item::usefee);
            case LEISURE_SPORTS -> plainText(intro, KtoTourApiResponse.Item::usefeeleports);
            default -> null;
        };
    }

    /** 대표메뉴는 firstmenu 를 쓰고, 비어 있을 때만 treatmenu 로 대신한다. */
    private String mainMenuOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        if (!RESTAURANT.equals(foreignTypeId)) {
            return null;
        }
        return firstNonBlank(plainText(intro, KtoTourApiResponse.Item::firstmenu),
                plainText(intro, KtoTourApiResponse.Item::treatmenu));
    }

    private String roomTypeOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        return LODGING.equals(foreignTypeId)
                ? plainText(intro, KtoTourApiResponse.Item::roomtype) : null;
    }

    private String mainProductsOf(KtoTourApiResponse.Item intro, String foreignTypeId) {
        return SHOPPING.equals(foreignTypeId)
                ? plainText(intro, KtoTourApiResponse.Item::saleitem) : null;
    }

    /**
     * 유형별 상세(detailIntro2). 있으면 좋은 값이라 실패해도 예외를 올리지 않는다.
     *
     * <p>외국어 유형 코드는 국문과 다르므로 매칭 결과로 받은 값을 그대로 쓴다. 값이 없으면 부르지 않는다.
     */
    private KtoTourApiResponse.Item introItem(KtoForeignLanguage language,
                                              String contentId, String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return null;
        }
        try {
            KtoTourApiResponse response = request(language, "/detailIntro2", builder -> builder
                    .queryParam("contentId", contentId)
                    .queryParam("contentTypeId", contentTypeId));
            List<KtoTourApiResponse.Item> items =
                    readItems(language, response.response().body().items());
            return items.isEmpty() ? null : items.get(0);
        } catch (RuntimeException exception) {
            // 상세가 없거나 조회에 실패해도 title/overview 자동입력은 그대로 둔다.
            return null;
        }
    }

    private String plainText(KtoTourApiResponse.Item intro,
                             Function<KtoTourApiResponse.Item, String> field) {
        return intro == null ? null : KtoTourTextSanitizer.toPlainText(field.apply(intro));
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private KtoForeignTourCandidateResponse toCandidate(KtoTourApiResponse.Item item,
                                                        double sourceLongitude,
                                                        double sourceLatitude) {
        String contentId = normalize(item.contentid());
        String title = KtoTourTextSanitizer.toPlainText(item.title());
        Double longitude = parseCandidateCoordinate(item.mapx());
        Double latitude = parseCandidateCoordinate(item.mapy());
        if (contentId == null || title == null || longitude == null || latitude == null) {
            return null;
        }
        return new KtoForeignTourCandidateResponse(
                contentId,
                normalize(item.contenttypeid()),
                title,
                normalize(item.mapx()),
                normalize(item.mapy()),
                distanceMeters(sourceLongitude, sourceLatitude, longitude, latitude));
    }

    private KtoTourApiResponse request(KtoForeignLanguage language, String path,
                                       Consumer<UriBuilder> specificParameters) {
        if (apiKey.isEmpty()) {
            throw KtoForeignTourApiException.missingApiKey(language);
        }
        try {
            KtoTourApiResponse response = restClients.get(language).get()
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
                                throw KtoForeignTourApiException.upstreamFailure(language);
                            })
                    .body(KtoTourApiResponse.class);
            validate(language, response);
            return response;
        } catch (KtoForeignTourApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw KtoForeignTourApiException.upstreamFailure(language);
        }
    }

    private void validate(KtoForeignLanguage language, KtoTourApiResponse response) {
        if (response == null || response.response() == null
                || response.response().header() == null || response.response().body() == null
                || !"0000".equals(response.response().header().resultCode())) {
            throw KtoForeignTourApiException.upstreamFailure(language);
        }
    }

    /** 결과가 없으면 items 가 객체가 아니라 빈 문자열로 오므로 그대로 빈 목록으로 본다. */
    private List<KtoTourApiResponse.Item> readItems(KtoForeignLanguage language,
                                                    JsonNode itemsNode) {
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
            throw KtoForeignTourApiException.upstreamFailure(language);
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
