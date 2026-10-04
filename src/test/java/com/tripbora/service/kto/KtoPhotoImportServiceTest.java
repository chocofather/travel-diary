package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.dto.kto.KtoTourImageCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KtoPhotoImportServiceTest {

    private static final Instant IMPORT_TIME = Instant.parse("2026-08-21T03:04:05Z");
    private static final String REQUEST_URL_A =
            "https://tong.visitkorea.or.kr/cms2/website/10/request-a.jpg";
    private static final String REQUEST_URL_B =
            "https://tong.visitkorea.or.kr/cms2/website/20/request-b.jpg";

    @Mock private KtoPhotoDownloadService downloadService;
    @Mock private KtoTourService ktoTourService;

    private KtoPhotoImportService importService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(IMPORT_TIME, ZoneId.of("Asia/Seoul"));
        importService = new KtoPhotoImportService(downloadService, ktoTourService, clock);
    }

    /** 관광사진 API 사진은 사진별 필드가 아니라 데이터셋 이용허락(공공누리 제1유형)을 근거로 저장한다. */
    @Test
    void photoGallerySelectionUsesTheDatasetLicenseWithoutAskingTourApi() {
        when(downloadService.download(REQUEST_URL_A)).thenReturn(downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg", REQUEST_URL_A));

        List<PreparedKtoPhoto> prepared = importService.preparePhotos(List.of(
                request("123", REQUEST_URL_A, "경복궁", "촬영자", true)));

        assertThat(prepared).extracting(PreparedKtoPhoto::sourceType, PreparedKtoPhoto::licenseType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("KTO_PHOTO_GALLERY", "KOGL_TYPE_1"));
        verifyNoMoreInteractions(ktoTourService);
    }

    /** TourAPI 사진은 화면 값을 믿지 않고 콘텐츠 이미지를 다시 조회해 사진별 저작권 코드로 정한다. */
    @Test
    void tourApiSelectionIsReverifiedAndKeepsTheType3License() {
        String type1Url = "https://tong.visitkorea.or.kr/cms/resource/10/type1_image2_1.jpg";
        String type3Url = "http://tong.visitkorea.or.kr/cms/resource/30/type3_image2_1.jpg";
        when(ktoTourService.getImportableImages("126508")).thenReturn(List.of(
                new KtoTourImageCandidate("126508", "경복궁", type1Url, "Type1", true),
                new KtoTourImageCandidate("126508", "경복궁 근정전", type3Url, "Type3", false)));
        when(downloadService.download(type1Url)).thenReturn(downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg", type1Url));
        when(downloadService.download(type3Url)).thenReturn(downloaded(
                "/uploads/destinations/22222222-2222-4222-8222-222222222222.jpg", type3Url));

        List<PreparedKtoPhoto> prepared = importService.preparePhotos(List.of(
                request("126508", type1Url, "경복궁", null, true),
                // 스킴만 다른 주소는 같은 원본으로 보고, 내려받기는 서버가 다시 받은 주소로 한다.
                request("126508", type3Url.replace("http:", "https:"), "경복궁", null, false)));

        assertThat(prepared).extracting(PreparedKtoPhoto::sourceType, PreparedKtoPhoto::licenseType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("KTO_TOURAPI", "KOGL_TYPE_1"),
                        org.assertj.core.groups.Tuple.tuple("KTO_TOURAPI", "KOGL_TYPE_3"));
        verify(ktoTourService).getImportableImages("126508");
        verify(downloadService).download(type3Url);
    }

    @Test
    void tourApiSelectionWithoutAllowedLicenseOrUnknownImageIsNotSaved() {
        String type2Url = "https://tong.visitkorea.or.kr/cms/resource/20/type2_image2_1.jpg";
        String type1Url = "https://tong.visitkorea.or.kr/cms/resource/10/type1_image2_1.jpg";
        when(ktoTourService.getImportableImages("200")).thenReturn(List.of(
                new KtoTourImageCandidate("200", "축제", type1Url, "Type1", true),
                new KtoTourImageCandidate("200", "축제", type2Url, "Type2", false)));
        when(downloadService.download(type1Url)).thenReturn(downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg", type1Url));

        assertThatThrownBy(() -> importService.preparePhotos(List.of(
                request("200", type1Url, "축제", null, false),
                request("200", type2Url, "축제", null, false))))
                .isInstanceOf(KtoPhotoImportException.class);
        // 앞서 받은 파일은 정리하고, 판정할 수 없는 사진은 내려받지 않는다.
        verify(downloadService).deleteDownloadedPhoto(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg");
        verify(downloadService, never()).download(type2Url);

        // TourAPI 목록에 없는 주소는 관광사진 API 경로가 아니므로 1유형으로 넘기지 않고 저장하지 않는다.
        assertThatThrownBy(() -> importService.preparePhotos(List.of(
                request("200", "https://tong.visitkorea.or.kr/cms/resource/99/other.jpg", "축제", null, false))))
                .isInstanceOf(KtoPhotoImportException.class);
    }

    @Test
    void emptySelectionReturnsNoPreparedPhotosWithoutDownload() {
        assertThat(importService.preparePhotos(List.of())).isEmpty();
        assertThat(importService.preparePhotos(null)).isEmpty();

        verifyNoMoreInteractions(downloadService);
    }

    @Test
    void preparesDownloadedPhotosInRequestOrderWithoutDestinationId() {
        KtoSelectedPhotoRequest first = request(
                "content-a", REQUEST_URL_A, "경복궁 봄", "촬영자 A", false);
        KtoSelectedPhotoRequest second = request(
                "content-b", REQUEST_URL_B, "경복궁 야경", "촬영자 B", true);
        KtoDownloadedPhoto firstDownload = downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                "https://tong.visitkorea.or.kr/cms2/website/10/validated-a.jpg");
        KtoDownloadedPhoto secondDownload = downloaded(
                "/uploads/destinations/22222222-2222-4222-8222-222222222222.png",
                "https://tong.visitkorea.or.kr/cms2/website/20/validated-b.png");
        when(downloadService.download(REQUEST_URL_A)).thenReturn(firstDownload);
        when(downloadService.download(REQUEST_URL_B)).thenReturn(secondDownload);

        List<PreparedKtoPhoto> prepared = importService.preparePhotos(List.of(first, second));

        InOrder order = inOrder(downloadService);
        order.verify(downloadService).download(REQUEST_URL_A);
        order.verify(downloadService).download(REQUEST_URL_B);
        assertThat(prepared)
                .extracting(
                        PreparedKtoPhoto::localImageUrl,
                        PreparedKtoPhoto::sourceImageUrl,
                        PreparedKtoPhoto::externalContentId,
                        PreparedKtoPhoto::title,
                        PreparedKtoPhoto::photographer,
                        PreparedKtoPhoto::isMain)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                firstDownload.localImageUrl(), firstDownload.sourceImageUrl(),
                                "content-a", "경복궁 봄", "촬영자 A", false),
                        org.assertj.core.groups.Tuple.tuple(
                                secondDownload.localImageUrl(), secondDownload.sourceImageUrl(),
                                "content-b", "경복궁 야경", "촬영자 B", true));
        assertThat(prepared)
                .extracting(PreparedKtoPhoto::licenseCheckedAt)
                .containsOnly(Timestamp.from(IMPORT_TIME));
        verify(downloadService, never()).deleteDownloadedPhoto(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void downloadFailureCleansPreparedLocalFilesAndReturnsNoPreparedResult() {
        KtoSelectedPhotoRequest first = request(
                "content-a", REQUEST_URL_A, "경복궁", "촬영자 A", false);
        KtoSelectedPhotoRequest second = request(
                "content-b", REQUEST_URL_B, "경복궁 야경", "촬영자 B", false);
        KtoDownloadedPhoto firstDownload = downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                REQUEST_URL_A);
        KtoPhotoDownloadException failure = new KtoPhotoDownloadException();
        when(downloadService.download(REQUEST_URL_A)).thenReturn(firstDownload);
        when(downloadService.download(REQUEST_URL_B)).thenThrow(failure);

        assertThatThrownBy(() -> importService.preparePhotos(List.of(first, second)))
                .isSameAs(failure);

        verify(downloadService).deleteDownloadedPhoto(firstDownload.localImageUrl());
    }

    @Test
    void cleanupFailureDoesNotReplaceOriginalDownloadFailure() {
        KtoSelectedPhotoRequest first = request("a", REQUEST_URL_A, "A", null, false);
        KtoSelectedPhotoRequest second = request("b", REQUEST_URL_B, "B", null, false);
        KtoDownloadedPhoto firstDownload = downloaded(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                REQUEST_URL_A);
        KtoPhotoDownloadException originalFailure = new KtoPhotoDownloadException();
        when(downloadService.download(REQUEST_URL_A)).thenReturn(firstDownload);
        when(downloadService.download(REQUEST_URL_B)).thenThrow(originalFailure);
        doThrow(new KtoPhotoDownloadException())
                .when(downloadService).deleteDownloadedPhoto(firstDownload.localImageUrl());

        assertThatThrownBy(() -> importService.preparePhotos(List.of(first, second)))
                .isSameAs(originalFailure);
    }

    @Test
    void cleanupPreparedPhotosUsesOnlyPreparedLocalUrls() {
        PreparedKtoPhoto first = prepared(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg");
        PreparedKtoPhoto second = prepared(
                "/uploads/destinations/22222222-2222-4222-8222-222222222222.png");

        importService.cleanupPreparedPhotos(List.of(first, second));

        verify(downloadService).deleteDownloadedPhoto(first.localImageUrl());
        verify(downloadService).deleteDownloadedPhoto(second.localImageUrl());
        verify(downloadService, never()).deleteDownloadedPhoto(org.mockito.ArgumentMatchers.any(KtoDownloadedPhoto.class));
    }

    private KtoSelectedPhotoRequest request(
            String contentId,
            String imageUrl,
            String title,
            String photographer,
            boolean main
    ) {
        return new KtoSelectedPhotoRequest(contentId, imageUrl, title, photographer, main);
    }

    private KtoDownloadedPhoto downloaded(String localImageUrl, String sourceImageUrl) {
        return new KtoDownloadedPhoto(localImageUrl, sourceImageUrl, "image/jpeg", 1024);
    }

    private PreparedKtoPhoto prepared(String localImageUrl) {
        return new PreparedKtoPhoto(
                localImageUrl,
                "https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                "content", "경복궁", "촬영자", false, Timestamp.from(IMPORT_TIME));
    }
}
