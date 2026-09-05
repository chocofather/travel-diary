package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoFestivalApiResponse;
import com.example.travlediary.dto.kto.KtoFestivalAdditionalImage;
import com.example.travlediary.dto.kto.KtoFestivalAutofillResponse;
import com.example.travlediary.dto.kto.KtoFestivalImageDetail;
import com.example.travlediary.dto.kto.KtoFestivalSearchItemResponse;
import com.example.travlediary.dto.kto.KtoFestivalSearchResponse;
import com.example.travlediary.dto.kto.KtoFestivalThumbnailCandidate;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class KtoFestivalService {

    private static final String FESTIVAL_CONTENT_TYPE_ID = "15";
    private static final int DETAIL_IMAGE_PAGE_SIZE = 100;
    private static final DateTimeFormatter TOUR_API_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public KtoFestivalService(RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper,
                              @Value("${kto.tour.api-key:}") String apiKey,
                              @Value("${kto.tour.kor-base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public KtoFestivalSearchResponse search(LocalDate eventStartDate, LocalDate eventEndDate,
                                            int pageNo, int numOfRows) {
        Objects.requireNonNull(eventStartDate, "eventStartDate must not be null");
        KtoFestivalApiResponse response = request("/searchFestival2", builder -> {
            builder.queryParam("eventStartDate", TOUR_API_DATE.format(eventStartDate))
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows);
            if (eventEndDate != null) {
                builder.queryParam("eventEndDate", TOUR_API_DATE.format(eventEndDate));
            }
        });

        return toSearchResponse(response, pageNo, numOfRows);
    }

    public KtoFestivalSearchResponse searchByKeyword(String keyword, int pageNo, int numOfRows) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword == null) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        KtoFestivalApiResponse response = request("/searchKeyword2", builder -> builder
                .queryParam("keyword", normalizedKeyword)
                .queryParam("contentTypeId", FESTIVAL_CONTENT_TYPE_ID)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows));
        return toSearchResponse(response, pageNo, numOfRows);
    }

    private KtoFestivalSearchResponse toSearchResponse(KtoFestivalApiResponse response,
                                                        int pageNo, int numOfRows) {
        KtoFestivalApiResponse.Body body = response.response().body();
        List<KtoFestivalSearchItemResponse> items = new ArrayList<>();
        for (KtoFestivalApiResponse.Item item : readItems(body.items())) {
            if (!FESTIVAL_CONTENT_TYPE_ID.equals(normalize(item.contenttypeid()))) {
                continue;
            }
            String lclsSystm1 = normalize(item.lclsSystm1());
            String lclsSystm2 = normalize(item.lclsSystm2());
            String lclsSystm3 = normalize(item.lclsSystm3());
            items.add(new KtoFestivalSearchItemResponse(
                    normalize(item.contentid()),
                    plainText(item.title()),
                    parseDate(item.eventstartdate()),
                    parseDate(item.eventenddate()),
                    normalize(item.firstimage()),
                    normalize(item.firstimage2()),
                    join(item.addr1(), item.addr2()),
                    lclsSystm1,
                    lclsSystm2,
                    lclsSystm3,
                    categoryName(lclsSystm1, lclsSystm2, lclsSystm3)
            ));
        }
        return new KtoFestivalSearchResponse(
                valueOrDefault(body.pageNo(), pageNo),
                valueOrDefault(body.numOfRows(), numOfRows),
                valueOrDefault(body.totalCount(), 0),
                List.copyOf(items)
        );
    }

    public KtoFestivalAutofillResponse getDetail(String contentId) {
        KtoFestivalApiResponse.Item common = getFestivalCommon(contentId);

        KtoFestivalApiResponse introResponse = request("/detailIntro2", builder -> builder
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", FESTIVAL_CONTENT_TYPE_ID));
        KtoFestivalApiResponse.Item intro = firstItem(introResponse.response().body().items());
        if (intro == null) {
            throw KtoTourApiException.upstreamFailure();
        }

        String lclsSystm1 = normalize(common.lclsSystm1());
        String lclsSystm2 = normalize(common.lclsSystm2());
        String lclsSystm3 = normalize(common.lclsSystm3());
        return new KtoFestivalAutofillResponse(
                firstNonBlank(common.contentid(), contentId),
                plainText(common.title()),
                parseDate(intro.eventstartdate()),
                parseDate(intro.eventenddate()),
                normalize(common.firstimage()),
                normalize(common.firstimage2()),
                join(common.addr1(), common.addr2()),
                plainText(intro.eventplace()),
                plainText(common.overview()),
                plainText(intro.playtime()),
                plainText(intro.usetimefestival()),
                plainText(intro.sponsor1()),
                plainText(intro.sponsor1tel()),
                plainText(intro.sponsor2()),
                plainText(intro.sponsor2tel()),
                homepageUrl(common.homepage()),
                homepageUrl(intro.eventhomepage()),
                plainText(common.tel()),
                lclsSystm1,
                lclsSystm2,
                lclsSystm3,
                categoryName(lclsSystm1, lclsSystm2, lclsSystm3)
        );
    }

    /**
     * 국문 축제 contentId 로 좌표만 복구한다. 외국어 축제 매칭이 좌표를 필요로 하는데
     * festival_info 에는 좌표를 두지 않기 때문이다.
     *
     * <p>기존 detailCommon2 호출을 그대로 쓴다. 조회에 실패하거나 좌표가 없으면 비어 있는 값을
     * 돌려주고, 부르는 쪽은 매칭 없음으로 처리한다.
     */
    public Optional<KtoFestivalCoordinates> getCoordinates(String contentId) {
        try {
            KtoFestivalApiResponse.Item common = getFestivalCommon(contentId);
            String mapX = normalize(common.mapx());
            String mapY = normalize(common.mapy());
            return mapX == null || mapY == null
                    ? Optional.empty()
                    : Optional.of(new KtoFestivalCoordinates(mapX, mapY));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public KtoFestivalImageDetail getImageDetail(String contentId) {
        KtoFestivalApiResponse.Item common = getFestivalCommon(contentId);
        return new KtoFestivalImageDetail(
                firstNonBlank(common.contentid(), contentId),
                plainText(common.title()),
                normalize(common.firstimage()),
                normalize(common.cpyrhtDivCd())
        );
    }

    public List<KtoFestivalAdditionalImage> getAdditionalImages(String contentId) {
        return getAdditionalImages(contentId, DETAIL_IMAGE_PAGE_SIZE);
    }

    public List<KtoFestivalThumbnailCandidate> getThumbnailCandidates(String contentId) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId == null) {
            throw KtoTourApiException.upstreamFailure();
        }
        KtoFestivalImageDetail imageDetail = getImageDetail(normalizedContentId);
        List<KtoFestivalAdditionalImage> additionalImages = getAdditionalImages(normalizedContentId);
        List<KtoFestivalThumbnailCandidate> candidates = new ArrayList<>();
        Set<String> sourceImageUrls = new LinkedHashSet<>();

        String mainImageUrl = normalize(imageDetail.firstImage());
        if (mainImageUrl != null) {
            sourceImageUrls.add(mainImageUrl);
            candidates.add(toThumbnailCandidate(
                    "MAIN",
                    mainImageUrl,
                    imageDetail.title(),
                    "대표사진",
                    imageDetail.copyrightDivisionCode()));
        }
        for (KtoFestivalAdditionalImage image : additionalImages) {
            if (image == null || !normalizedContentId.equals(normalize(image.contentId()))) {
                continue;
            }
            String sourceImageUrl = normalize(image.originalImageUrl());
            if (sourceImageUrl == null || !sourceImageUrls.add(sourceImageUrl)) {
                continue;
            }
            String serialNumber = normalize(image.serialNumber());
            candidates.add(toThumbnailCandidate(
                    serialNumber == null ? null : "DETAIL:" + serialNumber,
                    sourceImageUrl,
                    image.imageName(),
                    "추가사진",
                    image.copyrightDivisionCode()));
        }
        return List.copyOf(candidates);
    }

    List<KtoFestivalAdditionalImage> getAdditionalImages(String contentId, int pageSize) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId == null || pageSize <= 0) {
            throw KtoTourApiException.upstreamFailure();
        }

        List<KtoFestivalAdditionalImage> images = new ArrayList<>();
        int pageNo = 1;
        int processed = 0;
        while (true) {
            int requestedPage = pageNo;
            KtoFestivalApiResponse response = request("/detailImage2", builder -> builder
                    .queryParam("contentId", normalizedContentId)
                    .queryParam("imageYN", "Y")
                    .queryParam("pageNo", requestedPage)
                    .queryParam("numOfRows", pageSize));
            KtoFestivalApiResponse.Body body = response.response().body();
            List<KtoFestivalApiResponse.Item> pageItems = readItems(body.items());
            processed += pageItems.size();
            for (KtoFestivalApiResponse.Item item : pageItems) {
                if (!normalizedContentId.equals(normalize(item.contentid()))) {
                    continue;
                }
                String originalImageUrl = normalize(item.originimgurl());
                if (originalImageUrl == null) {
                    continue;
                }
                images.add(new KtoFestivalAdditionalImage(
                        normalizedContentId,
                        plainText(item.imgname()),
                        originalImageUrl,
                        normalize(item.serialnum()),
                        normalize(item.cpyrhtDivCd())
                ));
            }

            int totalCount = valueOrDefault(body.totalCount(), processed);
            if (pageItems.isEmpty() || pageItems.size() < pageSize || processed >= totalCount) {
                return List.copyOf(images);
            }
            pageNo++;
        }
    }

    private KtoFestivalThumbnailCandidate toThumbnailCandidate(String selectionKey,
                                                                String imageUrl,
                                                                String imageName,
                                                                String imageRole,
                                                                String copyrightDivisionCode) {
        KtoFestivalImageLicense license = KtoFestivalImageLicense
                .fromCopyrightDivisionCode(copyrightDivisionCode)
                .orElse(null);
        if (license == null) {
            return new KtoFestivalThumbnailCandidate(
                    selectionKey, imageUrl, imageName, imageRole, null, false,
                    "지원하지 않는 저작권 유형입니다.");
        }
        if (selectionKey == null) {
            return new KtoFestivalThumbnailCandidate(
                    null, imageUrl, imageName, imageRole, license.name(), false,
                    "이미지 식별값을 확인할 수 없습니다.");
        }
        return new KtoFestivalThumbnailCandidate(
                selectionKey, imageUrl, imageName, imageRole, license.name(), true, null);
    }

    private KtoFestivalApiResponse.Item getFestivalCommon(String contentId) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId == null) {
            throw KtoTourApiException.upstreamFailure();
        }
        KtoFestivalApiResponse commonResponse = request("/detailCommon2", builder -> builder
                .queryParam("contentId", normalizedContentId));
        KtoFestivalApiResponse.Item common = firstItem(commonResponse.response().body().items());
        if (common == null || !FESTIVAL_CONTENT_TYPE_ID.equals(normalize(common.contenttypeid()))) {
            throw KtoTourApiException.upstreamFailure();
        }
        return common;
    }

    private KtoFestivalApiResponse request(String path, Consumer<UriBuilder> specificParameters) {
        if (apiKey.isEmpty()) {
            throw KtoTourApiException.missingApiKey();
        }
        try {
            KtoFestivalApiResponse response = restClient.get()
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
                                throw KtoTourApiException.upstreamFailure();
                            })
                    .body(KtoFestivalApiResponse.class);
            validate(response);
            return response;
        } catch (KtoTourApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private void validate(KtoFestivalApiResponse response) {
        if (response == null || response.response() == null
                || response.response().header() == null || response.response().body() == null
                || !"0000".equals(response.response().header().resultCode())) {
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private List<KtoFestivalApiResponse.Item> readItems(JsonNode itemsNode) {
        if (itemsNode == null || !itemsNode.isObject()) {
            return List.of();
        }
        JsonNode itemNode = itemsNode.get("item");
        if (itemNode == null || itemNode.isNull() || itemNode.isTextual()) {
            return List.of();
        }
        List<KtoFestivalApiResponse.Item> items = new ArrayList<>();
        try {
            if (itemNode.isArray()) {
                for (JsonNode node : itemNode) {
                    items.add(objectMapper.treeToValue(node, KtoFestivalApiResponse.Item.class));
                }
            } else if (itemNode.isObject()) {
                items.add(objectMapper.treeToValue(itemNode, KtoFestivalApiResponse.Item.class));
            }
            return List.copyOf(items);
        } catch (Exception exception) {
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private KtoFestivalApiResponse.Item firstItem(JsonNode itemsNode) {
        List<KtoFestivalApiResponse.Item> items = readItems(itemsNode);
        return items.isEmpty() ? null : items.get(0);
    }

    private LocalDate parseDate(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, TOUR_API_DATE);
        } catch (DateTimeParseException exception) {
            throw KtoTourApiException.upstreamFailure();
        }
    }

    private String categoryName(String lclsSystm1, String lclsSystm2, String lclsSystm3) {
        String code = firstNonBlank(lclsSystm3, lclsSystm2, lclsSystm1);
        if (code == null) {
            return "기타행사";
        }
        String normalizedCode = code.toUpperCase(Locale.ROOT);
        if (normalizedCode.startsWith("EV01")) {
            return "축제";
        }
        if (normalizedCode.startsWith("EV02")) {
            return "공연";
        }
        return switch (normalizedCode) {
            case "EV030100", "EV030200" -> "전시·박람회";
            case "EV030300" -> "스포츠·대회";
            default -> "기타행사";
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

    private String plainText(String value) {
        return KtoTourTextSanitizer.toPlainText(value);
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
}
