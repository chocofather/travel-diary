package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.HomeFestivalDto;
import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse;
import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse.RegionPathItem;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.kto.KtoTourRegionMatchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HomeFestivalRegionServiceTest {
    private final KtoTourRegionMatchService matcher = mock(KtoTourRegionMatchService.class);
    private final ReferenceNameLocalizationService names = mock(ReferenceNameLocalizationService.class);
    private final HomeFestivalRegionService service = new HomeFestivalRegionService(matcher, names);

    @Test
    void usesVerifiedRegionTreeAndLocalizedNamesWithoutRoadOrDeeperNeighborhood() {
        var festival = festival("서울특별시 강남구 영동대로 513");
        when(matcher.match(festival.getFestivalInfo().getAddress())).thenReturn(
                KtoTourRegionMatchResponse.matched(List.of(new RegionPathItem(1L, "대한민국"),
                        new RegionPathItem(2L, "서울"), new RegionPathItem(3L, "강남구"),
                        new RegionPathItem(4L, "삼성동"))));
        when(names.localizeCountryCategoryNames(Map.of(2L, "서울", 3L, "강남구"), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(2L, "Seoul", 3L, "Gangnam-gu"));
        assertThat(service.resolveLocations(List.of(festival), SupportedLanguage.ENGLISH))
                .containsEntry(9L, "Seoul Gangnam-gu");
        verify(names).localizeCountryCategoryNames(Map.of(2L, "서울", 3L, "강남구"), SupportedLanguage.ENGLISH);
    }

    @Test
    void unrecognizedAddressNeverFallsBackToTheFullStreetAddress() {
        var festival = festival("123 Unknown Road, Somewhere");
        when(matcher.match(festival.getFestivalInfo().getAddress()))
                .thenReturn(KtoTourRegionMatchResponse.unmatched());
        when(names.localizeCountryCategoryNames(Map.of(), SupportedLanguage.KOREAN)).thenReturn(Map.of());
        assertThat(service.resolveLocations(List.of(festival), SupportedLanguage.KOREAN)).isEmpty();
    }

    private HomeFestivalDto festival(String address) {
        var festival = new HomeFestivalDto();
        festival.setId(9L);
        var info = new FestivalInfo();
        info.setInfoId(9L);
        info.setAddress(address);
        festival.setFestivalInfo(info);
        return festival;
    }
}
