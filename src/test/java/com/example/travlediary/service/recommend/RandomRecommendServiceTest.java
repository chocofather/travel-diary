package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelRegionDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.recommend.RandomRecommendMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.destination.DestinationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RandomRecommendServiceTest {

    @Mock
    private RandomRecommendMapper mapper;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private DestinationService destinationService;
    @Mock
    private ReferenceNameLocalizationService referenceNameLocalizationService;

    @Test
    void existingRegionRecommendationKeepsItsListContract() {
        RandomDestinationDto destination = destination(41L, "경복궁");
        when(countryCategoryService.getAllRegionIdsUnder(31L)).thenReturn(List.of(31L, 32L));
        when(mapper.findRandomByRegionIds(List.of(31L, 32L), 5))
                .thenReturn(List.of(destination));

        List<RandomDestinationDto> result = service().getRandomDestinationsByRegion(31L, 5);

        assertThat(result).containsExactly(destination);
    }

    @Test
    void domesticScopeSelectsOnlyAnEligibleDirectChildOfTheDynamicKoreaRoot() {
        CountryCategory korea = country(909L, "대한민국", null);
        CountryCategory japan = country(808L, "일본", 301L);
        RandomTravelRegionDto seoul = region(909L, "대한민국", 910L, "서울");
        RandomDestinationDto palace = destination(41L, "경복궁");
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan));
        when(mapper.findRandomEligibleChildRegion(909L, 777L)).thenReturn(seoul);
        when(mapper.findAllVisibleRegionIdsUnder(910L)).thenReturn(List.of(910L, 911L, 912L));
        when(mapper.findRandomByRegionIds(List.of(910L, 911L, 912L), 8))
                .thenReturn(List.of(palace));

        var result = service().getRandomTravelByScope("domestic", 777L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getScope()).isEqualTo("domestic");
        assertThat(result.orElseThrow().getCountryId()).isEqualTo(909L);
        assertThat(result.orElseThrow().getRegionId()).isEqualTo(910L);
        assertThat(result.orElseThrow().getRegionName()).isEqualTo("서울");
        assertThat(result.orElseThrow().getRecommendedDestinations()).containsExactly(palace);
        verify(mapper).findRandomEligibleChildRegion(909L, 777L);
    }

    @Test
    void overseasScopeSelectsAnActualCountryBeforeItsEligibleDirectChild() {
        CountryCategory korea = country(909L, "대한민국", null);
        CountryCategory japan = country(808L, "일본", 301L);
        CountryCategory france = country(707L, "프랑스", 302L);
        RandomTravelRegionDto selectedCountry = region(808L, "일본", 808L, "일본");
        RandomTravelRegionDto osaka = region(808L, "일본", 811L, "오사카");
        RandomDestinationDto castle = destination(51L, "오사카성");
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan, france));
        when(mapper.findRandomEligibleCountry(List.of(808L, 707L), 812L))
                .thenReturn(selectedCountry);
        when(mapper.findRandomEligibleChildRegion(808L, 812L)).thenReturn(osaka);
        when(mapper.findAllVisibleRegionIdsUnder(811L)).thenReturn(List.of(811L, 813L));
        when(mapper.findRandomByRegionIds(List.of(811L, 813L), 8))
                .thenReturn(List.of(castle));

        var result = service().getRandomTravelByScope("OVERSEAS", 812L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getScope()).isEqualTo("overseas");
        assertThat(result.orElseThrow().getCountryName()).isEqualTo("일본");
        assertThat(result.orElseThrow().getRegionName()).isEqualTo("오사카");
        assertThat(result.orElseThrow().getRecommendedDestinations()).containsExactly(castle);
        verify(mapper).findRandomEligibleCountry(List.of(808L, 707L), 812L);
        verify(mapper).findRandomEligibleChildRegion(808L, 812L);
    }

    @Test
    void overseasScopeFallsBackToTheCountryWhenItHasNoEligibleChildRegion() {
        CountryCategory korea = country(909L, "대한민국", null);
        CountryCategory iceland = country(606L, "아이슬란드", 303L);
        RandomTravelRegionDto selectedCountry = region(606L, "아이슬란드", 606L, "아이슬란드");
        RandomDestinationDto lagoon = destination(61L, "블루 라군");
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, iceland));
        when(mapper.findRandomEligibleCountry(List.of(606L), null)).thenReturn(selectedCountry);
        when(mapper.findRandomEligibleChildRegion(606L, null)).thenReturn(null);
        when(mapper.findAllVisibleRegionIdsUnder(606L)).thenReturn(List.of(606L));
        when(mapper.findRandomByRegionIds(List.of(606L), 8)).thenReturn(List.of(lagoon));

        var result = service().getRandomTravelByScope("overseas", null);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getCountryId()).isEqualTo(606L);
        assertThat(result.orElseThrow().getRegionId()).isEqualTo(606L);
        assertThat(result.orElseThrow().getRecommendedDestinations()).containsExactly(lagoon);
    }

    @Test
    void returnsEmptyWhenNoDomesticRegionHasADestination() {
        when(countryCategoryService.getCourseCountries())
                .thenReturn(List.of(country(909L, "대한민국", null)));
        when(mapper.findRandomEligibleChildRegion(909L, null)).thenReturn(null);

        assertThat(service().getRandomTravelByScope("domestic", null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoOverseasCountryHasADestination() {
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(
                country(909L, "대한민국", null),
                country(808L, "일본", 301L)));
        when(mapper.findRandomEligibleCountry(List.of(808L), null)).thenReturn(null);

        assertThat(service().getRandomTravelByScope("overseas", null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheSelectedRegionCannotProduceCards() {
        CountryCategory korea = country(909L, "대한민국", null);
        RandomTravelRegionDto seoul = region(909L, "대한민국", 910L, "서울");
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea));
        when(mapper.findRandomEligibleChildRegion(909L, null)).thenReturn(seoul);
        when(mapper.findAllVisibleRegionIdsUnder(910L)).thenReturn(List.of(910L));
        when(mapper.findRandomByRegionIds(List.of(910L), 8)).thenReturn(List.of());

        assertThat(service().getRandomTravelByScope("domestic", null)).isEmpty();
    }

    @Test
    void rejectsUnsupportedScopeBeforeQuerying() {
        assertThatThrownBy(() -> service().getRandomTravelByScope("all", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("여행 범위");
        verifyNoInteractions(mapper, countryCategoryService);
    }

    @Test
    void localizesAllRandomCardsAndSelectedRegionsInBatchesWithoutChangingOrder() {
        CountryCategory korea = country(909L, "대한민국", null);
        RandomTravelRegionDto seoul = region(909L, "대한민국", 910L, "서울");
        List<RandomDestinationDto> cards = List.of(
                destination(7L, "경복궁", 910L, "서울"),
                destination(3L, "창덕궁", 910L, "서울"),
                destination(9L, "서울숲", 910L, "서울"),
                destination(2L, "남산", 910L, "서울"));
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea));
        when(mapper.findRandomEligibleChildRegion(909L, null)).thenReturn(seoul);
        when(mapper.findAllVisibleRegionIdsUnder(910L)).thenReturn(List.of(910L));
        when(mapper.findRandomByRegionIds(List.of(910L), 8)).thenReturn(cards);
        when(destinationService.resolveLocalizedContentByDestinationIds(
                List.of(7L, 3L, 9L, 2L), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(
                        7L, translation(7L, "Gyeongbokgung Palace", "Korean summary fallback"),
                        3L, translation(3L, "Changdeokgung Palace", "Changdeokgung summary"),
                        9L, translation(9L, "Seoul Forest", "Seoul Forest summary"),
                        2L, translation(2L, "Namsan", "Namsan summary")));
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                Map.of(909L, "대한민국", 910L, "서울"), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(909L, "South Korea", 910L, "Seoul"));

        var result = service().getRandomTravelByScope(
                "domestic", null, SupportedLanguage.ENGLISH).orElseThrow();

        assertThat(result.getCountryName()).isEqualTo("South Korea");
        assertThat(result.getRegionName()).isEqualTo("Seoul");
        assertThat(result.getRecommendedDestinations())
                .extracting(RandomDestinationDto::getDestinationId)
                .containsExactly(7L, 3L, 9L, 2L);
        assertThat(result.getRecommendedDestinations())
                .extracting(RandomDestinationDto::getDestinationName)
                .containsExactly("Gyeongbokgung Palace", "Changdeokgung Palace",
                        "Seoul Forest", "Namsan");
        assertThat(result.getRecommendedDestinations().get(0).getShortDescription())
                .isEqualTo("Korean summary fallback");
        assertThat(result.getRecommendedDestinations())
                .extracting(RandomDestinationDto::getRegionName)
                .containsOnly("Seoul");
        verify(destinationService).resolveLocalizedContentByDestinationIds(
                List.of(7L, 3L, 9L, 2L), SupportedLanguage.ENGLISH);
        verify(referenceNameLocalizationService).localizeCountryCategoryNames(
                Map.of(909L, "대한민국", 910L, "서울"), SupportedLanguage.ENGLISH);
    }

    @Test
    void missingTranslationsKeepEveryRandomCardAndItsBaseValues() {
        when(countryCategoryService.getAllRegionIdsUnder(31L)).thenReturn(List.of(31L));
        RandomDestinationDto palace = destination(41L, "경복궁", 31L, "서울");
        when(mapper.findRandomByRegionIds(List.of(31L), 5)).thenReturn(List.of(palace));
        when(destinationService.resolveLocalizedContentByDestinationIds(
                List.of(41L), SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(41L, translation(41L, null, null)));
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                Map.of(909L, "대한민국", 31L, "서울"), SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(909L, "대한민국", 31L, "서울"));

        List<RandomDestinationDto> result = service().getRandomDestinationsByRegion(
                31L, 5, SupportedLanguage.JAPANESE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDestinationName()).isEqualTo("경복궁");
        assertThat(result.get(0).getShortDescription()).isEqualTo("경복궁 설명");
        assertThat(result.get(0).getRegionName()).isEqualTo("서울");
    }

    private RandomRecommendService service() {
        return new RandomRecommendService(mapper, countryCategoryService,
                destinationService, referenceNameLocalizationService);
    }

    private CountryCategory country(Long id, String name, Long parentId) {
        CountryCategory country = new CountryCategory();
        country.setId(id);
        country.setRegionName(name);
        country.setParentId(parentId);
        return country;
    }

    private RandomTravelRegionDto region(
            Long countryId, String countryName, Long regionId, String regionName) {
        RandomTravelRegionDto region = new RandomTravelRegionDto();
        region.setCountryId(countryId);
        region.setCountryName(countryName);
        region.setRegionId(regionId);
        region.setRegionName(regionName);
        return region;
    }

    private RandomDestinationDto destination(Long id, String name) {
        RandomDestinationDto destination = new RandomDestinationDto();
        destination.setDestinationId(id);
        destination.setDestinationName(name);
        destination.setShortDescription(name + " 설명");
        destination.setImageUrl("/uploads/" + id + ".jpg");
        return destination;
    }

    private RandomDestinationDto destination(
            Long id, String name, Long regionId, String regionName) {
        RandomDestinationDto destination = destination(id, name);
        destination.setCountryId(909L);
        destination.setCountryName("대한민국");
        destination.setRegionId(regionId);
        destination.setRegionName(regionName);
        return destination;
    }

    private DestinationTranslation translation(
            Long destinationId, String name, String shortDescription) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setName(name);
        translation.setShortDescription(shortDescription);
        return translation;
    }
}
