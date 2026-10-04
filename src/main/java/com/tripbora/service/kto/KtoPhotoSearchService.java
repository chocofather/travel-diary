package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoPhotoSearchItemResponse;
import com.tripbora.dto.kto.KtoPhotoSearchResponse;
import com.tripbora.dto.kto.KtoTourContentImages;
import com.tripbora.dto.kto.KtoTourImageCandidate;
import com.tripbora.model.DestinationImageLicenseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 관리자 "한국관광공사 관광사진" 검색. 검색창 하나로 두 API의 사진을 한 그리드에 보여준다.
 *
 * <p>두 API는 라이선스 판정 근거가 다르다.</p>
 * <ul>
 *   <li>관광사진 API(PhotoGalleryService1): 사진별 저작권 필드가 없다. 공식 API 데이터셋 이용허락범위를
 *       근거로 TYPE1 처리한다({@link KtoPhotoGalleryService#DATASET_LICENSE_TYPE}).</li>
 *   <li>국문 TourAPI(KorService2): 응답의 사진별 저작권 구분 코드(cpyrhtDivCd)를 근거로 판정한다
 *       ({@link KtoFestivalImageLicense}). 1유형·3유형만 남기고 2·4유형과 판정 불가 사진은 뺀다.</li>
 * </ul>
 *
 * <p>기존 검색 품질을 지키려고 관광사진 API 결과를 먼저 두고, TourAPI 사진은 첫 페이지에만 덧붙인다.
 * 더보기는 그대로 관광사진 API 페이지를 넘긴다. TourAPI 를 쓰지 못하면 관광사진 API 결과만 준다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KtoPhotoSearchService {

    public static final String TOUR_SOURCE_TYPE = "KTO_TOURAPI";
    private static final String TOUR_SOURCE_NAME = "한국관광공사";
    /** 사진을 모을 TourAPI 콘텐츠 수. 콘텐츠마다 detailImage2 를 한 번 더 부른다. */
    static final int TOUR_CONTENT_LIMIT = 3;
    /** 한 콘텐츠에 추가이미지가 많아도 첫 페이지가 지나치게 길어지지 않게 한다. */
    static final int MAX_TOUR_PHOTOS = 30;

    private final KtoPhotoGalleryService ktoPhotoGalleryService;
    private final KtoTourService ktoTourService;

    public KtoPhotoSearchResponse search(String keyword, int pageNo, int numOfRows) {
        KtoPhotoSearchResponse gallery = ktoPhotoGalleryService.search(keyword, pageNo, numOfRows);
        if (pageNo != 1) {
            return gallery;
        }

        List<KtoPhotoSearchItemResponse> merged = new ArrayList<>();
        Set<String> seenImages = new HashSet<>();
        for (KtoPhotoSearchItemResponse item : gallery.items()) {
            if (seenImages.add(imageKey(item.imageUrl()))) {
                merged.add(item);
            }
        }
        int galleryCount = merged.size();
        for (KtoPhotoSearchItemResponse item : tourItems(keyword)) {
            if (merged.size() - galleryCount >= MAX_TOUR_PHOTOS) {
                break;
            }
            if (seenImages.add(imageKey(item.imageUrl()))) {
                merged.add(item);
            }
        }

        // 더보기 판단(불러온 수 >= 전체 수)이 맞도록 덧붙인 TourAPI 사진 수를 전체 수에 더한다.
        int tourCount = merged.size() - galleryCount;
        return new KtoPhotoSearchResponse(
                gallery.pageNo(),
                gallery.numOfRows(),
                gallery.totalCount() + tourCount,
                List.copyOf(merged)
        );
    }

    private List<KtoPhotoSearchItemResponse> tourItems(String keyword) {
        List<KtoTourContentImages> contents;
        try {
            contents = ktoTourService.searchContentImages(keyword, TOUR_CONTENT_LIMIT);
        } catch (KtoTourApiException exception) {
            log.warn("TourAPI 관광사진을 불러오지 못해 관광사진 API 결과만 보여줍니다. (원인: {})",
                    exception.getMessage());
            return List.of();
        }

        List<KtoPhotoSearchItemResponse> items = new ArrayList<>();
        for (KtoTourContentImages content : contents) {
            for (KtoTourImageCandidate image : content.images()) {
                tourItem(content, image).ifPresent(items::add);
            }
        }
        return items;
    }

    /** 응답의 사진별 저작권 구분 코드를 근거로 판정한다. 1·3유형이 아니면 결과에 넣지 않는다. */
    private Optional<KtoPhotoSearchItemResponse> tourItem(KtoTourContentImages content,
                                                          KtoTourImageCandidate image) {
        return KtoFestivalImageLicense.fromCopyrightDivisionCode(image.copyrightDivisionCode())
                .map(license -> new KtoPhotoSearchItemResponse(
                        content.contentId(),
                        content.title(),
                        image.imageUrl(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TOUR_SOURCE_TYPE,
                        TOUR_SOURCE_NAME,
                        license.name(),
                        DestinationImageLicenseType.displayName(license.name())
                ));
    }

    /**
     * 같은 원본 이미지를 가리키는지 보는 키. 같은 호스트의 http/https 는 같은 이미지로 본다.
     *
     * <p>콘텐츠 ID는 두 API의 식별 체계가 달라(galContentId / contentid) 서로 비교하지 않고,
     * 제목이 같다는 이유로도 제거하지 않는다.</p>
     */
    static String imageKey(String imageUrl) {
        String value = imageUrl == null ? "" : imageUrl.strip();
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null) {
                String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                return uri.getHost().toLowerCase(Locale.ROOT) + uri.getRawPath() + query;
            }
        } catch (IllegalArgumentException exception) {
            // 해석할 수 없는 값은 문자열 그대로 비교한다.
        }
        return value;
    }
}
