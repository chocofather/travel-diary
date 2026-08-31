package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FestivalDetailServiceTest {

    @Mock
    private TravelInfoService travelInfoService;
    @Mock
    private TravelInfoMapper travelInfoMapper;
    @Mock
    private FestivalInfoMapper festivalInfoMapper;

    private FestivalDetailService service;

    @BeforeEach
    void setUp() {
        service = new FestivalDetailService(travelInfoService, travelInfoMapper, festivalInfoMapper);
    }

    @Test
    void publicFestivalDetailComposesCommonPeriodFestivalInfoAndOrderedImages() {
        TravelInfoDetailDto preview = detail(TravelInfoContentType.FESTIVAL);
        TravelInfoDetailDto common = detail(TravelInfoContentType.FESTIVAL);
        TravelInfoPeriodDto period = new TravelInfoPeriodDto(
                LocalDate.parse("2026-09-02"), LocalDate.parse("2026-10-24"));
        common.setPeriods(List.of(period));
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(10L);
        festivalInfo.setEventPlace("경복궁");
        InfoImage mainImage = new InfoImage();
        mainImage.setImageUrl("/uploads/travel-info/festivals/local.jpg");
        mainImage.setIsMain(true);
        mainImage.setOrderIndex(1);
        InfoImage additionalImage = new InfoImage();
        additionalImage.setImageUrl("/uploads/travel-info/festivals/additional.jpg");
        additionalImage.setIsMain(false);
        additionalImage.setOrderIndex(2);
        when(travelInfoMapper.findPublicDetailById(10L)).thenReturn(preview);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(common);
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(festivalInfo);
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(mainImage, additionalImage));

        FestivalDetailDto result = service.getPublicDetail(10L);

        assertThat(result.getTravelInfo()).isSameAs(common);
        assertThat(result.getPrimaryPeriod()).isSameAs(period);
        assertThat(result.getFestivalInfo()).isSameAs(festivalInfo);
        assertThat(result.getMainImage()).isSameAs(mainImage);
        assertThat(result.getImages()).containsExactly(mainImage, additionalImage);
        assertThat(result.isGallery()).isTrue();
        assertThat(result.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void generalContentIsRejectedBeforeViewsOrFestivalTablesAreTouched() {
        when(travelInfoMapper.findPublicDetailById(10L))
                .thenReturn(detail(TravelInfoContentType.GENERAL));

        assertThatThrownBy(() -> service.getPublicDetail(10L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(travelInfoService, never()).getPublicDetail(10L);
        verify(festivalInfoMapper, never()).findByInfoId(10L);
        verify(travelInfoMapper, never()).findImagesByInfoId(10L);
    }

    @Test
    void viewModelNormalizesBlankRowsRejectsUnsafeHomepageAndMapsKoglLabels() {
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setEventPlace("   ");
        festivalInfo.setAddress(" 서울 종로구 ");
        festivalInfo.setSponsor1Tel(" 02-1111-2222 ");
        festivalInfo.setHomepageUrl("javascript:alert(1)");
        InfoImage type1 = new InfoImage();
        type1.setLicenseType("KOGL_TYPE_1");
        FestivalDetailDto detail = new FestivalDetailDto(
                detail(TravelInfoContentType.FESTIVAL), festivalInfo, type1);

        assertThat(detail.getEventPlace()).isNull();
        assertThat(detail.getAddress()).isEqualTo("서울 종로구");
        assertThat(detail.getSponsor1()).isNull();
        assertThat(detail.getSponsor1Tel()).isEqualTo("02-1111-2222");
        festivalInfo.setAddress(null);
        assertThat(detail.isEventInfoPresent()).isTrue();
        assertThat(detail.getHomepageUrl()).isNull();
        assertThat(detail.getLicenseLabel()).isEqualTo("공공누리 제1유형");

        type1.setLicenseType("KOGL_TYPE_3");
        assertThat(detail.getLicenseLabel()).isEqualTo("공공누리 제3유형");
    }

    @Test
    void galleryViewModelsKeepDatabaseOrderAndMapEachImagesAttribution() {
        InfoImage main = image("/uploads/main.jpg", true, 1, "KOGL_TYPE_1");
        InfoImage additional = image("/uploads/additional.jpg", false, 2, "KOGL_TYPE_3");
        FestivalDetailDto detail = new FestivalDetailDto(
                detail(TravelInfoContentType.FESTIVAL), new FestivalInfo(), List.of(main, additional));

        assertThat(detail.getGalleryImages())
                .extracting("imageUrl", "main", "orderIndex", "sourceName", "licenseLabel")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "/uploads/main.jpg", true, 1, "한국관광공사", "공공누리 제1유형"),
                        org.assertj.core.groups.Tuple.tuple(
                                "/uploads/additional.jpg", false, 2, "한국관광공사", "공공누리 제3유형"));
        assertThat(detail.isGallery()).isTrue();
        assertThat(detail.isGalleryAttributionPresent()).isTrue();
    }

    private InfoImage image(String url, boolean main, int orderIndex, String licenseType) {
        InfoImage image = new InfoImage();
        image.setImageUrl(url);
        image.setIsMain(main);
        image.setOrderIndex(orderIndex);
        image.setSourceType("KTO_TOURAPI");
        image.setSourceName("한국관광공사");
        image.setLicenseType(licenseType);
        return image;
    }

    private TravelInfoDetailDto detail(TravelInfoContentType contentType) {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("경복궁 별빛야행");
        detail.setContentType(contentType);
        return detail;
    }
}
