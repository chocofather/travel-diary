package com.tripbora.service.destination;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.kto.KtoDownloadedPhoto;
import com.tripbora.service.kto.KtoPhotoDownloadException;
import com.tripbora.service.kto.KtoPhotoDownloadService;
import com.tripbora.service.kto.PhotoDownloadRateLimitedException;
import com.tripbora.service.pixabay.PixabayApiException;
import com.tripbora.service.pixabay.PixabayHit;
import com.tripbora.service.pixabay.PixabayImageSearchService;
import com.tripbora.service.pixabay.PixabayImageSearchService.PixabaySearchResult;
import com.tripbora.service.pixabay.PixabayPhotoException;
import com.tripbora.service.pixabay.PreparedPixabayPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationPixabayImageManagementServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private PixabayImageSearchService searchService;
    @Mock private KtoPhotoDownloadService downloadService;
    @Mock private DestinationSavePersistenceService persistence;
    @Mock private DestinationImageService images;
    @Mock private DestinationCommonsImageManagementService commons;
    @Mock private DestinationMapper mapper;
    @Mock private CountryCategoryService countryCategoryService;

    private DestinationPixabayImageManagementService service;

    @BeforeEach
    void setUp() {
        service = new DestinationPixabayImageManagementService(searchService, downloadService, persistence, images,
                commons, mapper, countryCategoryService, CLOCK, Runnable::run);
        when(searchService.isConfigured()).thenReturn(true);
        when(images.getImages(9L)).thenReturn(List.of());
    }

    @Test
    void onlyTheSelectedIdsAreDownloadedFromServerSideSearchResultsAndSavedWithPixabayMetadata() {
        PixabayHit hit = hit(195893, null, null, "https://pixabay.com/get/l_1280.jpg");
        when(searchService.findSearchedHit(195893)).thenReturn(Optional.of(hit));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/l_1280.jpg"))
                .thenReturn(downloaded("/uploads/destinations/aaa.jpg", "https://pixabay.com/get/l_1280.jpg"));

        assertThat(service.addPhotos(9L, List.of(195893L))).isEqualTo(1);

        // 기본 계정 응답처럼 imageURL·fullHDURL 이 없으면 largeImageURL 하나만 받는다(없는 URL을 만들어 부르지 않는다).
        verify(downloadService, times(1)).downloadPixabayImage(anyString());
        PreparedPixabayPhoto photo = savedPhotos().get(0);
        assertThat(photo.localImageUrl()).isEqualTo("/uploads/destinations/aaa.jpg");
        // 여행지에 대표 사진이 없으면 첫 사진을 대표로 둔다.
        assertThat(photo.main()).isTrue();
        DestinationImageCommonsSource source = photo.source();
        assertThat(source.getSourceName()).isEqualTo("Pixabay");
        assertThat(source.getExternalContentId()).isEqualTo("195893");
        assertThat(source.getWorkPageUrl()).isEqualTo("https://pixabay.com/photos/eiffel-195893/");
        assertThat(source.getAuthorText()).isEqualTo("Hans");
        assertThat(source.getOriginalImageUrl()).isEqualTo("https://pixabay.com/get/l_1280.jpg");
        assertThat(source.getLicenseType()).isEqualTo("PIXABAY_CONTENT_LICENSE").doesNotContain("CC0");
        assertThat(source.getLicenseName()).isEqualTo("Pixabay Content License");
        assertThat(source.getLicenseUrl()).isEqualTo("https://pixabay.com/service/license-summary/");
        assertThat(source.getAttributionText()).isEqualTo("사진: Hans / Pixabay");
        assertThat(source.getLicenseEvidenceDetail()).contains("largeImageURL");
        assertThat(source.getCommonsFileTitle()).isNull();
        assertThat(source.getWikidataQid()).isNull();
        assertThat(source.getLicenseCheckedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void theBestUrlInTheResponseIsUsedFirstAndOnlyTheNextOneIsTriedWhenItCannotBeSaved() {
        PixabayHit hit = hit(7, "https://pixabay.com/get/original.jpg", "https://pixabay.com/get/f_1920.jpg",
                "https://pixabay.com/get/l_1280.jpg");
        when(searchService.findSearchedHit(7)).thenReturn(Optional.of(hit));
        when(images.getImages(9L)).thenReturn(List.of(image(true, "ADMIN_UPLOAD", null)));
        // 원본이 저장 한도를 넘으면 그다음 판(fullHDURL)을 받는다.
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/original.jpg"))
                .thenThrow(new KtoPhotoDownloadException("용량 제한 초과"));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/f_1920.jpg"))
                .thenReturn(downloaded("/uploads/destinations/bbb.jpg", "https://pixabay.com/get/f_1920.jpg"));

        service.addPhotos(9L, List.of(7L));

        verify(downloadService, never()).downloadPixabayImage("https://pixabay.com/get/l_1280.jpg");
        PreparedPixabayPhoto photo = savedPhotos().get(0);
        assertThat(photo.source().getOriginalImageUrl()).isEqualTo("https://pixabay.com/get/f_1920.jpg");
        // 이미 대표 사진이 있으면 바꾸지 않는다.
        assertThat(photo.main()).isFalse();
    }

    @Test
    void idsThatWereNotReturnedByARecentSearchAreRejectedBeforeAnyDownload() {
        when(searchService.findSearchedHit(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(123L)))
                .isInstanceOf(PixabayPhotoException.class)
                .hasMessageContaining("123");
        assertThatThrownBy(() -> service.addPhotos(9L, List.of(-5L))).isInstanceOf(PixabayPhotoException.class);
        assertThatThrownBy(() -> service.addPhotos(9L, List.of(3L, 3L))).isInstanceOf(PixabayPhotoException.class);
        assertThatThrownBy(() -> service.addPhotos(9L, List.of())).isInstanceOf(PixabayPhotoException.class);
        assertThatThrownBy(() -> service.addPhotos(9L, java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList()))
                .isInstanceOf(PixabayPhotoException.class);
        verify(downloadService, never()).downloadPixabayImage(anyString());
        verify(persistence, never()).addPixabayPhotosToExistingDestination(any(), anyList());
    }

    @Test
    void aPhotoAlreadySavedForThisDestinationCannotBeSavedAgain() {
        when(images.getImages(9L)).thenReturn(List.of(image(false, "PIXABAY", "195893")));
        when(searchService.findSearchedHit(195893)).thenReturn(Optional.of(hit(195893, null, null, "https://pixabay.com/get/l.jpg")));

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(195893L)))
                .isInstanceOf(PixabayPhotoException.class)
                .hasMessageContaining("이미 이 여행지에 등록된 Pixabay 사진");
        verify(downloadService, never()).downloadPixabayImage(anyString());
    }

    @Test
    void rateLimitedDownloadStopsWithoutTryingOtherUrlsAndRemovesFilesAlreadyDownloaded() {
        when(searchService.findSearchedHit(1)).thenReturn(Optional.of(hit(1, null, null, "https://pixabay.com/get/one.jpg")));
        when(searchService.findSearchedHit(2)).thenReturn(Optional.of(hit(2, null, "https://pixabay.com/get/two_1920.jpg",
                "https://pixabay.com/get/two_1280.jpg")));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/one.jpg"))
                .thenReturn(downloaded("/uploads/destinations/one.jpg", "https://pixabay.com/get/one.jpg"));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/two_1920.jpg"))
                .thenThrow(new PhotoDownloadRateLimitedException("30"));

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(1L, 2L)))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.RATE_LIMITED))
                .hasMessage("Pixabay 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        verify(downloadService, never()).downloadPixabayImage("https://pixabay.com/get/two_1280.jpg");
        verify(downloadService).deleteDownloadedPhoto("/uploads/destinations/one.jpg");
        verify(persistence, never()).addPixabayPhotosToExistingDestination(any(), anyList());
    }

    @Test
    void downloadFailureOfEveryUrlSavesNothing() {
        when(searchService.findSearchedHit(4)).thenReturn(Optional.of(hit(4, null, null, "https://pixabay.com/get/four.jpg")));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/four.jpg"))
                .thenThrow(new KtoPhotoDownloadException("HTTP 상태 404"));

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(4L)))
                .isInstanceOf(PixabayPhotoException.class)
                .hasMessageContaining("ID 4")
                .hasMessageContaining("HTTP 상태 404");
        verify(persistence, never()).addPixabayPhotosToExistingDestination(any(), anyList());
    }

    @Test
    void duplicateFoundInsideTheSaveTransactionRemovesTheDownloadedFiles() {
        when(searchService.findSearchedHit(5)).thenReturn(Optional.of(hit(5, null, null, "https://pixabay.com/get/five.jpg")));
        when(downloadService.downloadPixabayImage("https://pixabay.com/get/five.jpg"))
                .thenReturn(downloaded("/uploads/destinations/five.jpg", "https://pixabay.com/get/five.jpg"));
        doThrow(new PixabayPhotoException("이미 이 여행지에 등록된 Pixabay 사진입니다: ID 5"))
                .when(persistence).addPixabayPhotosToExistingDestination(eq(9L), anyList());

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(5L))).isInstanceOf(PixabayPhotoException.class);
        verify(downloadService).deleteDownloadedPhoto("/uploads/destinations/five.jpg");
    }

    @Test
    void missingApiKeyRejectsSavingBeforeAnythingElse() {
        when(searchService.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.addPhotos(9L, List.of(1L)))
                .isInstanceOfSatisfying(PixabayApiException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PixabayApiException.Reason.NOT_CONFIGURED));
        verify(searchService, never()).findSearchedHit(anyLong());
    }

    @Test
    void searchResultsMarkPhotosAlreadySavedForThisDestination() {
        when(images.getImages(9L)).thenReturn(List.of(image(false, "PIXABAY", "2"), image(false, "KTO_PHOTO_GALLERY", "1")));
        when(searchService.search("Seoul", 0)).thenReturn(new PixabaySearchResult("Seoul", 0, 2,
                List.of(hit(1, null, null, "https://pixabay.com/get/1.jpg"), hit(2, null, null, "https://pixabay.com/get/2.jpg")),
                null));

        var page = service.search(9L, "Seoul", 0);

        assertThat(page.photos()).extracting(DestinationPixabayImageManagementService.Photo::registered)
                .containsExactly(false, true);
        // 화면에는 미리보기 주소만 보낸다. 저장용 주소(largeImageURL 등)는 보내지 않는다.
        assertThat(page.photos().get(0).thumbnailUrl()).isEqualTo("https://pixabay.com/get/w1_640.jpg");
        assertThat(page.selectionLimit()).isEqualTo(20);
    }

    @Test
    void defaultQueryPrefersStoredEnglishNamesForBothOverseasAndDomesticDestinations() {
        givenDestination(1L, true, List.of(translation("ko", "에펠탑"), translation("en", "Eiffel Tower")),
                List.of(region("유럽", "Europe", 1), region("프랑스", "France", 2), region("파리", "Paris", 3)));
        assertThat(service.defaultSearchQuery(1L)).isEqualTo("Eiffel Tower Paris France");

        givenDestination(2L, false, List.of(translation("ko", "경복궁"), translation("en", "Gyeongbokgung Palace")),
                List.of(region("대한민국", "South Korea", 1), region("서울", "Seoul", 2), region("종로구", "Jongno-gu", 3)));
        assertThat(service.defaultSearchQuery(2L)).isEqualTo("Gyeongbokgung Palace Seoul South Korea");
        // Commons 기본 검색어 규칙은 쓰지 않는다(Commons 동작과 분리).
        verify(commons, never()).defaultSearchQuery(anyLong());
    }

    @Test
    void defaultQuerySkipsRegionsWithoutEnglishNamesAndFallsBackToKoreanOnlyWithoutAnEnglishName() {
        // 영문 지역명이 일부 없으면 있는 영문 정보까지만 붙인다(한국어를 섞지 않는다).
        givenDestination(3L, false, List.of(translation("ko", "경복궁"), translation("en", "Gyeongbokgung Palace")),
                List.of(region("대한민국", "South Korea", 1), region("서울", null, 2)));
        assertThat(service.defaultSearchQuery(3L)).isEqualTo("Gyeongbokgung Palace South Korea");

        // 영문 이름에 이미 들어 있는 지역명은 다시 붙이지 않는다.
        givenDestination(4L, false, List.of(translation("en", "N Seoul Tower")),
                List.of(region("대한민국", "South Korea", 1), region("서울", "Seoul", 2)));
        assertThat(service.defaultSearchQuery(4L)).isEqualTo("N Seoul Tower South Korea");

        // 영문 여행지명 자체가 없을 때만 한국어 여행지명 + 한국어 지역명이다.
        givenDestination(5L, false, List.of(translation("ko", "경복궁"), translation("en", "  ")),
                List.of(region("대한민국", "South Korea", 1), region("서울", "Seoul", 2), region("종로구", "Jongno-gu", 3)));
        assertThat(service.defaultSearchQuery(5L)).isEqualTo("경복궁 서울");
        givenDestination(6L, true, List.of(translation("ko", "에펠탑")),
                List.of(region("유럽", "Europe", 1), region("프랑스", "France", 2), region("파리", "Paris", 3)));
        assertThat(service.defaultSearchQuery(6L)).isEqualTo("에펠탑 파리 프랑스");
    }

    private void givenDestination(Long id, boolean overseas, List<DestinationTranslation> translations,
                                  List<CountryCategory> path) {
        when(commons.isOverseasDestination(id)).thenReturn(overseas);
        when(mapper.findTranslationsByDestinationId(id)).thenReturn(translations);
        Destination destination = new Destination();
        destination.setRegionId(id * 10);
        when(mapper.findById(id)).thenReturn(destination);
        when(countryCategoryService.getRegionPath(id * 10)).thenReturn(path);
    }

    private List<PreparedPixabayPhoto> savedPhotos() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PreparedPixabayPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistence).addPixabayPhotosToExistingDestination(eq(9L), captor.capture());
        return captor.getValue();
    }

    private static PixabayHit hit(long id, String imageUrl, String fullHdUrl, String largeImageUrl) {
        return new PixabayHit(id, "https://pixabay.com/photos/eiffel-" + id + "/", null,
                "https://pixabay.com/get/w" + id + "_640.jpg", largeImageUrl, fullHdUrl, imageUrl,
                4000, 3000, "Hans", "eiffel, paris");
    }

    private static KtoDownloadedPhoto downloaded(String localUrl, String sourceUrl) {
        return new KtoDownloadedPhoto(localUrl, sourceUrl, "image/jpeg", 1000L);
    }

    private static DestinationImage image(boolean main, String sourceType, String externalContentId) {
        DestinationImage image = new DestinationImage();
        image.setIsMain(main);
        image.setSourceType(sourceType);
        image.setExternalContentId(externalContentId);
        return image;
    }

    private static DestinationTranslation translation(String language, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setLanguageCode(language);
        translation.setName(name);
        return translation;
    }

    private static CountryCategory region(String name, String nameEn, int depth) {
        CountryCategory region = new CountryCategory();
        region.setRegionName(name);
        region.setNameEn(nameEn);
        region.setDepth(depth);
        return region;
    }
}
