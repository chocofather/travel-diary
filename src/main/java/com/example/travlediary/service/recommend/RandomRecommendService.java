package com.example.travlediary.service.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.repository.recommend.RandomRecommendMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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
}