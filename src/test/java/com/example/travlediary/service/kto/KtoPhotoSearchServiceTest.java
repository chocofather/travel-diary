package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoPhotoSearchItemResponse;
import com.example.travlediary.dto.kto.KtoPhotoSearchResponse;
import com.example.travlediary.dto.kto.KtoTourContentImages;
import com.example.travlediary.dto.kto.KtoTourImageCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KtoPhotoSearchServiceTest {

    private static final String GALLERY_URL = "https://tong.visitkorea.or.kr/cms2/website/75/1002175.jpg";

    @Mock private KtoPhotoGalleryService ktoPhotoGalleryService;
    @Mock private KtoTourService ktoTourService;

    private KtoPhotoSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new KtoPhotoSearchService(ktoPhotoGalleryService, ktoTourService);
    }

    @Test
    void firstPageKeepsGalleryResultsFirstAndAddsOnlyType1AndType3TourPhotos() {
        when(ktoPhotoGalleryService.search("경복궁", 1, 12)).thenReturn(gallery(533, galleryItem("1002175", GALLERY_URL)));
        when(ktoTourService.searchContentImages("경복궁", KtoPhotoSearchService.TOUR_CONTENT_LIMIT)).thenReturn(List.of(
                new KtoTourContentImages("126508", "경복궁", List.of(
                        tourImage("126508", "/cms/resource/1/type1.jpg", "Type1"),
                        tourImage("126508", "/cms/resource/3/type3.jpg", "Type3"),
                        tourImage("126508", "/cms/resource/2/type2.jpg", "Type2"),
                        tourImage("126508", "/cms/resource/4/type4.jpg", "Type4"),
                        tourImage("126508", "/cms/resource/0/blank.jpg", " "),
                        tourImage("126508", "/cms/resource/0/null.jpg", null),
                        tourImage("126508", "/cms/resource/0/unknown.jpg", "KOGL1")))));

        KtoPhotoSearchResponse response = searchService.search("경복궁", 1, 12);

        assertThat(response.items())
                .extracting(KtoPhotoSearchItemResponse::sourceType, KtoPhotoSearchItemResponse::licenseType,
                        KtoPhotoSearchItemResponse::licenseLabel)
                .containsExactly(
                        tuple("KTO_PHOTO_GALLERY", "KOGL_TYPE_1", "공공누리 제1유형"),
                        tuple("KTO_TOURAPI", "KOGL_TYPE_1", "공공누리 제1유형"),
                        tuple("KTO_TOURAPI", "KOGL_TYPE_3", "공공누리 제3유형 · 변경금지"));
        assertThat(response.items().get(2))
                .extracting(KtoPhotoSearchItemResponse::externalContentId, KtoPhotoSearchItemResponse::title,
                        KtoPhotoSearchItemResponse::sourceName)
                .containsExactly("126508", "경복궁", "한국관광공사");
        // 더보기 판단이 맞도록 덧붙인 TourAPI 사진 수만큼 전체 수가 늘어난다.
        assertThat(response.totalCount()).isEqualTo(535);
    }

    @Test
    void laterPagesStayGalleryOnlySoPaginationIsUnchanged() {
        KtoPhotoSearchResponse galleryPage = gallery(533, galleryItem("1002176", GALLERY_URL));
        when(ktoPhotoGalleryService.search("경복궁", 2, 12)).thenReturn(galleryPage);

        assertThat(searchService.search("경복궁", 2, 12)).isSameAs(galleryPage);
        verify(ktoTourService, never()).searchContentImages(anyString(), anyInt());
    }

    /** 같은 원본(스킴만 다른 주소 포함)만 제거하고, 제목이나 콘텐츠 ID가 같다는 이유로는 지우지 않는다. */
    @Test
    void removesOnlyTheSameOriginalImage() {
        when(ktoPhotoGalleryService.search("남이섬", 1, 12)).thenReturn(gallery(2,
                galleryItem("100", GALLERY_URL),
                galleryItem("100", GALLERY_URL.replace("https:", "http:"))));
        when(ktoTourService.searchContentImages("남이섬", KtoPhotoSearchService.TOUR_CONTENT_LIMIT)).thenReturn(List.of(
                new KtoTourContentImages("100", "남이섬", List.of(
                        tourImage("100", "/cms/resource/1/a.jpg", "Type3"),
                        tourImage("100", "/cms/resource/1/b.jpg", "Type3"))),
                new KtoTourContentImages("200", "남이섬", List.of(
                        new KtoTourImageCandidate("200", "남이섬", "HTTPS://TONG.visitkorea.or.kr/cms/resource/1/a.jpg",
                                "Type3", true)))));

        KtoPhotoSearchResponse response = searchService.search("남이섬", 1, 12);

        assertThat(response.items())
                .extracting(KtoPhotoSearchItemResponse::externalContentId, KtoPhotoSearchItemResponse::imageUrl)
                .containsExactly(
                        tuple("100", GALLERY_URL),
                        tuple("100", "http://tong.visitkorea.or.kr/cms/resource/1/a.jpg"),
                        tuple("100", "http://tong.visitkorea.or.kr/cms/resource/1/b.jpg"));
        assertThat(response.totalCount()).isEqualTo(4);
    }

    @Test
    void tourApiFailureFallsBackToGalleryResults() {
        KtoPhotoSearchResponse galleryPage = gallery(1, galleryItem("1002175", GALLERY_URL));
        when(ktoPhotoGalleryService.search("경복궁", 1, 12)).thenReturn(galleryPage);
        when(ktoTourService.searchContentImages("경복궁", KtoPhotoSearchService.TOUR_CONTENT_LIMIT))
                .thenThrow(KtoTourApiException.missingApiKey());

        KtoPhotoSearchResponse response = searchService.search("경복궁", 1, 12);

        assertThat(response.items()).containsExactlyElementsOf(galleryPage.items());
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    void tourPhotosAreCappedSoTheFirstPageStaysShort() {
        List<KtoTourImageCandidate> images = new ArrayList<>();
        for (int index = 0; index < KtoPhotoSearchService.MAX_TOUR_PHOTOS + 5; index++) {
            images.add(tourImage("300", "/cms/resource/5/" + index + ".jpg", "Type1"));
        }
        when(ktoPhotoGalleryService.search("순천만", 1, 12)).thenReturn(gallery(0));
        when(ktoTourService.searchContentImages("순천만", KtoPhotoSearchService.TOUR_CONTENT_LIMIT))
                .thenReturn(List.of(new KtoTourContentImages("300", "순천만습지", images)));

        KtoPhotoSearchResponse response = searchService.search("순천만", 1, 12);

        assertThat(response.items()).hasSize(KtoPhotoSearchService.MAX_TOUR_PHOTOS);
        assertThat(response.totalCount()).isEqualTo(KtoPhotoSearchService.MAX_TOUR_PHOTOS);
    }

    private KtoPhotoSearchResponse gallery(int totalCount, KtoPhotoSearchItemResponse... items) {
        return new KtoPhotoSearchResponse(1, 12, totalCount, List.of(items));
    }

    private KtoPhotoSearchItemResponse galleryItem(String contentId, String imageUrl) {
        return new KtoPhotoSearchItemResponse(contentId, "경복궁 수문장 교대의식", imageUrl, "201004", "서울",
                "한국관광공사 김지호", "경복궁", null, null,
                "KTO_PHOTO_GALLERY", "한국관광공사", "KOGL_TYPE_1", "공공누리 제1유형");
    }

    /** 이미지 이름에 워터마크 표기가 있든 없든 판정은 저작권 구분 코드만 본다. */
    private KtoTourImageCandidate tourImage(String contentId, String path, String copyrightCode) {
        return new KtoTourImageCandidate(contentId, "워터마크 없음", "http://tong.visitkorea.or.kr" + path,
                copyrightCode, false);
    }
}
