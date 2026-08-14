package com.example.travlediary.service.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelRegionDto;
import com.example.travlediary.dto.RandomTravelResultDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.recommend.RandomRecommendMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RandomRecommendService {
    private final RandomRecommendMapper randomRecommendMapper;
    private final CountryCategoryService countryCategoryService;

    public RandomRecommendService(
            RandomRecommendMapper randomRecommendMapper,
            CountryCategoryService countryCategoryService) {
        this.randomRecommendMapper = randomRecommendMapper;
        this.countryCategoryService = countryCategoryService;
    }

    // 특정 regionId 및 하위 모든 지역의 여행지 중 랜덤 N개
    public List<RandomDestinationDto> getRandomDestinationsByRegion(Long regionId, int limit) {
        List<Long> allRegionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
        if (allRegionIds == null || allRegionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return randomRecommendMapper.findRandomByRegionIds(allRegionIds, limit);
    }

    public Optional<RandomTravelResultDto> getRandomTravelByScope(
            String scope, Long excludeRegionId) {
        String normalizedScope = scope == null ? "" : scope.toLowerCase(Locale.ROOT);
        if (!normalizedScope.equals("domestic") && !normalizedScope.equals("overseas")) {
            throw new IllegalArgumentException("지원하지 않는 여행 범위입니다.");
        }

        List<CountryCategory> actualCountries = countryCategoryService.getCourseCountries();
        RandomTravelRegionDto selectedRegion = normalizedScope.equals("domestic")
                ? selectDomesticRegion(actualCountries, excludeRegionId)
                : selectOverseasRegion(actualCountries, excludeRegionId);
        if (selectedRegion == null) {
            return Optional.empty();
        }

        List<Long> regionIds = randomRecommendMapper.findAllVisibleRegionIdsUnder(
                selectedRegion.getRegionId());
        if (regionIds == null || regionIds.isEmpty()) {
            return Optional.empty();
        }

        List<RandomDestinationDto> destinations =
                randomRecommendMapper.findRandomByRegionIds(regionIds, 8);
        if (destinations == null || destinations.isEmpty()) {
            return Optional.empty();
        }

        RandomTravelResultDto result = new RandomTravelResultDto();
        result.setScope(normalizedScope);
        result.setCountryId(selectedRegion.getCountryId());
        result.setCountryName(selectedRegion.getCountryName());
        result.setRegionId(selectedRegion.getRegionId());
        result.setRegionName(selectedRegion.getRegionName());
        result.setRecommendedDestinations(destinations);
        return Optional.of(result);
    }

    private RandomTravelRegionDto selectDomesticRegion(
            List<CountryCategory> actualCountries, Long excludeRegionId) {
        return actualCountries.stream()
                .filter(country -> country.getParentId() == null)
                .findFirst()
                .map(country -> randomRecommendMapper.findRandomEligibleChildRegion(
                        country.getId(), excludeRegionId))
                .orElse(null);
    }

    private RandomTravelRegionDto selectOverseasRegion(
            List<CountryCategory> actualCountries, Long excludeRegionId) {
        List<Long> overseasCountryIds = actualCountries.stream()
                .filter(country -> country.getParentId() != null)
                .map(CountryCategory::getId)
                .toList();
        if (overseasCountryIds.isEmpty()) {
            return null;
        }

        RandomTravelRegionDto country = randomRecommendMapper.findRandomEligibleCountry(
                overseasCountryIds, excludeRegionId);
        if (country == null) {
            return null;
        }

        RandomTravelRegionDto childRegion = randomRecommendMapper.findRandomEligibleChildRegion(
                country.getCountryId(), excludeRegionId);
        return childRegion == null ? country : childRegion;
    }
}
