package com.example.travlediary.repository.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelRegionDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RandomRecommendMapper {

    List<RandomDestinationDto> findRandomByRegion(
            @Param("regionId") Long regionId,
            @Param("limit") int limit
    );

    // 이 부분 추가!!
    List<RandomDestinationDto> findRandomByRegionIds(
            @Param("regionIds") List<Long> regionIds,
            @Param("limit") int limit
    );

    RandomTravelRegionDto findRandomEligibleCountry(
            @Param("countryIds") List<Long> countryIds,
            @Param("excludeRegionId") Long excludeRegionId
    );

    RandomTravelRegionDto findRandomEligibleChildRegion(
            @Param("countryId") Long countryId,
            @Param("excludeRegionId") Long excludeRegionId
    );

    List<Long> findAllVisibleRegionIdsUnder(@Param("regionId") Long regionId);
}
