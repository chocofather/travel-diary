package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.recommend.DestinationRecommendMapper;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.destination.DestinationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationRecommendServiceLocalizationTest {

    @Mock private DestinationRecommendMapper recommendMapper;
    @Mock private DestinationService destinationService;
    @Mock private ReferenceNameLocalizationService referenceNameLocalizationService;

    @Test
    void localizesSeasonDestinationNameRegionAndExistingCategoryInBatches() {
        SeasonDestinationDto palace = new SeasonDestinationDto();
        palace.setId(15L);
        palace.setName("경복궁");
        palace.setRegionId(235L);
        palace.setRegionName("종로구");
        palace.setCategoryId(7L);
        palace.setCategoryName("랜드마크");
        palace.setSeason("SPRING");
        palace.setImageUrl("/palace.jpg");
        when(recommendMapper.findBySeasonAndCategory("SPRING", 7L, 5))
                .thenReturn(List.of(palace));
        when(destinationService.resolveLocalizedContentByDestinationIds(
                List.of(15L), SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(15L, translation(15L, "景福宮")));
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                Map.of(235L, "종로구"), SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(235L, "鐘路区"));
        when(referenceNameLocalizationService.localizeCategories(
                List.of(7L), SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(7L, "ランドマーク"));

        DestinationRecommendService service = new DestinationRecommendService(
                recommendMapper, destinationService, referenceNameLocalizationService);

        SeasonDestinationDto result = service.findBySeasonAndCategory(
                "SPRING", 7L, 5, SupportedLanguage.JAPANESE).get(0);

        assertThat(result.getName()).isEqualTo("景福宮");
        assertThat(result.getRegionName()).isEqualTo("鐘路区");
        assertThat(result.getCategoryName()).isEqualTo("ランドマーク");
        assertThat(result.getSeason()).isEqualTo("SPRING");
        assertThat(result.getImageUrl()).isEqualTo("/palace.jpg");
        verify(destinationService).resolveLocalizedContentByDestinationIds(
                List.of(15L), SupportedLanguage.JAPANESE);
        verify(referenceNameLocalizationService).localizeCountryCategoryNames(
                Map.of(235L, "종로구"), SupportedLanguage.JAPANESE);
        verify(referenceNameLocalizationService).localizeCategories(
                List.of(7L), SupportedLanguage.JAPANESE);
    }

    @Test
    void localizesParentRegionInTheSameBatchAsTheRegion() {
        SeasonDestinationDto gongju = new SeasonDestinationDto();
        gongju.setId(21L);
        gongju.setName("공산성");
        gongju.setRegionId(371L);
        gongju.setRegionName("공주시");
        gongju.setParentRegionId(49L);
        gongju.setParentRegionName("충남");
        SeasonDestinationDto tokyo = new SeasonDestinationDto();
        tokyo.setId(22L);
        tokyo.setName("아사쿠사");
        tokyo.setRegionId(92L);
        tokyo.setRegionName("도쿄");
        tokyo.setParentRegionId(8L);
        tokyo.setParentRegionName("일본");
        when(recommendMapper.findBySeason("FALL", 5)).thenReturn(List.of(gongju, tokyo));
        when(destinationService.resolveLocalizedContentByDestinationIds(
                List.of(21L, 22L), SupportedLanguage.ENGLISH)).thenReturn(Map.of());
        Map<Long, String> baseRegionNames = new java.util.LinkedHashMap<>();
        baseRegionNames.put(371L, "공주시");
        baseRegionNames.put(49L, "충남");
        baseRegionNames.put(92L, "도쿄");
        baseRegionNames.put(8L, "일본");
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                baseRegionNames, SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(371L, "Gongju-si", 49L, "Chungnam", 92L, "Tokyo", 8L, "Japan"));
        when(referenceNameLocalizationService.localizeCategories(
                List.of(), SupportedLanguage.ENGLISH)).thenReturn(Map.of());

        DestinationRecommendService service = new DestinationRecommendService(
                recommendMapper, destinationService, referenceNameLocalizationService);

        List<SeasonDestinationDto> result = service.findBySeason("FALL", 5, SupportedLanguage.ENGLISH);

        assertThat(result.get(0).getParentRegionName()).isEqualTo("Chungnam");
        assertThat(result.get(0).getRegionName()).isEqualTo("Gongju-si");
        assertThat(result.get(1).getParentRegionName()).isEqualTo("Japan");
        assertThat(result.get(1).getRegionName()).isEqualTo("Tokyo");
        // 상위 지역을 따로 조회하지 않고 지역과 같은 한 번의 호출로 번역한다.
        verify(referenceNameLocalizationService).localizeCountryCategoryNames(
                baseRegionNames, SupportedLanguage.ENGLISH);
    }

    private DestinationTranslation translation(Long destinationId, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setName(name);
        return translation;
    }
}
