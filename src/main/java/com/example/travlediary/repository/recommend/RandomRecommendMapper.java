package com.example.travlediary.repository.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
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
}

