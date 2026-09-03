package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelRegionDto;
import com.example.travlediary.dto.RandomTravelResultDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.recommend.RandomRecommendMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.destination.DestinationService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@Service
public class RandomRecommendService {
    private final RandomRecommendMapper randomRecommendMapper;
    private final CountryCategoryService countryCategoryService;
    private final DestinationService destinationService;
    private final ReferenceNameLocalizationService referenceNameLocalizationService;

    public RandomRecommendService(
            RandomRecommendMapper randomRecommendMapper,
            CountryCategoryService countryCategoryService,
            DestinationService destinationService,
            ReferenceNameLocalizationService referenceNameLocalizationService) {
        this.randomRecommendMapper = randomRecommendMapper;
        this.countryCategoryService = countryCategoryService;
        this.destinationService = destinationService;
        this.referenceNameLocalizationService = referenceNameLocalizationService;
    }

    // 특정 regionId 및 하위 모든 지역의 여행지 중 랜덤 N개
    public List<RandomDestinationDto> getRandomDestinationsByRegion(Long regionId, int limit) {
        List<Long> allRegionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
        if (allRegionIds == null || allRegionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return randomRecommendMapper.findRandomByRegionIds(allRegionIds, limit);
    }

    public List<RandomDestinationDto> getRandomDestinationsByRegion(
            Long regionId, int limit, SupportedLanguage requestedLanguage) {
        List<RandomDestinationDto> destinations =
                getRandomDestinationsByRegion(regionId, limit);
        Map<Long, String> localizedRegionNames =
                referenceNameLocalizationService.localizeCountryCategoryNames(
                        collectBaseRegionNames(destinations), requestedLanguage);
        return localizeDestinations(
                destinations, requestedLanguage, localizedRegionNames);
    }

    public Optional<RandomTravelResultDto> getRandomTravelByScope(
            String scope, Long excludeRegionId) {
        return findRandomTravelByScope(scope, excludeRegionId);
    }

    public Optional<RandomTravelResultDto> getRandomTravelByScope(
            String scope,
            Long excludeRegionId,
            SupportedLanguage requestedLanguage) {
        return findRandomTravelByScope(scope, excludeRegionId)
                .map(result -> localizeResult(result, requestedLanguage));
    }

    private Optional<RandomTravelResultDto> findRandomTravelByScope(
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

    private RandomTravelResultDto localizeResult(
            RandomTravelResultDto result,
            SupportedLanguage requestedLanguage) {
        Map<Long, String> baseRegionNames = new LinkedHashMap<>();
        if (result.getCountryId() != null) {
            baseRegionNames.put(result.getCountryId(), result.getCountryName());
        }
        if (result.getRegionId() != null) {
            baseRegionNames.put(result.getRegionId(), result.getRegionName());
        }
        collectBaseRegionNames(result.getRecommendedDestinations())
                .forEach(baseRegionNames::putIfAbsent);
        Map<Long, String> localizedRegionNames =
                referenceNameLocalizationService.localizeCountryCategoryNames(
                        baseRegionNames, requestedLanguage);
        List<RandomDestinationDto> localizedDestinations = localizeDestinations(
                result.getRecommendedDestinations(), requestedLanguage, localizedRegionNames);
        if (result.getCountryId() != null) {
            result.setCountryName(localizedRegionNames.getOrDefault(
                    result.getCountryId(), result.getCountryName()));
        }
        if (result.getRegionId() != null) {
            result.setRegionName(localizedRegionNames.getOrDefault(
                    result.getRegionId(), result.getRegionName()));
        }
        result.setRecommendedDestinations(localizedDestinations);
        return result;
    }

    private List<RandomDestinationDto> localizeDestinations(
            List<RandomDestinationDto> destinations,
            SupportedLanguage requestedLanguage,
            Map<Long, String> sharedBaseRegionNames) {
        List<RandomDestinationDto> available = destinations == null ? List.of() : destinations;
        List<Long> destinationIds = available.stream()
                .filter(destination -> destination != null
                        && destination.getDestinationId() != null)
                .map(RandomDestinationDto::getDestinationId)
                .distinct()
                .toList();
        Map<Long, DestinationTranslation> localizedContent =
                destinationService.resolveLocalizedContentByDestinationIds(
                        destinationIds, requestedLanguage);
        Map<Long, String> localizedRegionNames = sharedBaseRegionNames;

        for (RandomDestinationDto destination : available) {
            if (destination == null) {
                continue;
            }
            DestinationTranslation content = localizedContent.get(destination.getDestinationId());
            if (content != null) {
                if (content.getName() != null && !content.getName().isBlank()) {
                    destination.setDestinationName(content.getName());
                }
                if (content.getShortDescription() != null
                        && !content.getShortDescription().isBlank()) {
                    destination.setShortDescription(content.getShortDescription());
                }
            }
            if (destination.getCountryId() != null) {
                destination.setCountryName(localizedRegionNames.getOrDefault(
                        destination.getCountryId(), destination.getCountryName()));
            }
            if (destination.getRegionId() != null) {
                destination.setRegionName(localizedRegionNames.getOrDefault(
                        destination.getRegionId(), destination.getRegionName()));
            }
        }
        return available;
    }

    private Map<Long, String> collectBaseRegionNames(
            List<RandomDestinationDto> destinations) {
        Map<Long, String> baseRegionNames = new LinkedHashMap<>();
        List<RandomDestinationDto> available = destinations == null ? List.of() : destinations;
        for (RandomDestinationDto destination : available) {
            if (destination == null) {
                continue;
            }
            if (destination.getCountryId() != null) {
                baseRegionNames.putIfAbsent(
                        destination.getCountryId(), destination.getCountryName());
            }
            if (destination.getRegionId() != null) {
                baseRegionNames.putIfAbsent(
                        destination.getRegionId(), destination.getRegionName());
            }
        }
        return baseRegionNames;
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
