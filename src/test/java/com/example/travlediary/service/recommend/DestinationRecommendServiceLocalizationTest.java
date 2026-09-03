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

    private DestinationTranslation translation(Long destinationId, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setName(name);
        return translation;
    }
}
