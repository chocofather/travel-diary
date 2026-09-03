package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.recommend.PopularRecommendMapper;
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
class PopularRecommendServiceLocalizationTest {

    @Mock private PopularRecommendMapper recommendMapper;
    @Mock private DestinationService destinationService;
    @Mock private ReferenceNameLocalizationService referenceNameLocalizationService;

    @Test
    void localizesPopularDestinationNamesAndRegionsWithoutChangingRankingData() {
        RecommendDestinationDto palace = destination(
                15L, 235L, "경복궁", "종로구", 459, 31);
        RecommendDestinationDto village = destination(
                16L, 236L, "북촌한옥마을", "중구", 300, 20);
        when(recommendMapper.findPopularDomesticDestinations(2))
                .thenReturn(List.of(palace, village));
        when(destinationService.resolveLocalizedContentByDestinationIds(
                List.of(15L, 16L), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(
                        15L, translation(15L, "Gyeongbokgung Palace"),
                        16L, translation(16L, "Bukchon Hanok Village")));
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                Map.of(235L, "종로구", 236L, "중구"), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(235L, "Jongno-gu", 236L, "Jung-gu"));

        PopularRecommendService service = new PopularRecommendService(
                recommendMapper, destinationService, referenceNameLocalizationService);

        List<RecommendDestinationDto> result =
                service.findDomesticPopular(2, SupportedLanguage.ENGLISH);

        assertThat(result).extracting(RecommendDestinationDto::getName)
                .containsExactly("Gyeongbokgung Palace", "Bukchon Hanok Village");
        assertThat(result).extracting(RecommendDestinationDto::getRegionName)
                .containsExactly("Jongno-gu", "Jung-gu");
        assertThat(result).extracting(RecommendDestinationDto::getViews)
                .containsExactly(459, 300);
        assertThat(result).extracting(RecommendDestinationDto::getBookmarkCount)
                .containsExactly(31, 20);
        verify(destinationService).resolveLocalizedContentByDestinationIds(
                List.of(15L, 16L), SupportedLanguage.ENGLISH);
        verify(referenceNameLocalizationService).localizeCountryCategoryNames(
                Map.of(235L, "종로구", 236L, "중구"), SupportedLanguage.ENGLISH);
    }

    private RecommendDestinationDto destination(Long id, Long regionId, String name,
                                                  String regionName, int views,
                                                  int bookmarkCount) {
        RecommendDestinationDto destination = new RecommendDestinationDto();
        destination.setId(id);
        destination.setRegionId(regionId);
        destination.setName(name);
        destination.setRegionName(regionName);
        destination.setViews(views);
        destination.setBookmarkCount(bookmarkCount);
        destination.setImageUrl("/image-" + id + ".jpg");
        return destination;
    }

    private DestinationTranslation translation(Long destinationId, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setName(name);
        return translation;
    }
}
