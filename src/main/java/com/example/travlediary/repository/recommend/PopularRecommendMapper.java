package com.example.travlediary.repository.recommend;

import com.example.travlediary.dto.RecommendDestinationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PopularRecommendMapper {

    // 국내 인기 여행지 (상위 N개 랜덤)
    List<RecommendDestinationDto> findPopularDomesticDestinations(@Param("limit") int limit);

    // 해외 인기 여행지 (상위 N개 랜덤)
    List<RecommendDestinationDto> findPopularOverseasDestinations(@Param("limit") int limit);

    // 테마/카테고리별 인기 여행지 (여러 카테고리 id IN으로 전달)
    List<RecommendDestinationDto> findPopularThemeDestinations(@Param("categoryIds") List<Long> categoryIds,
                                                               @Param("limit") int limit);
}
