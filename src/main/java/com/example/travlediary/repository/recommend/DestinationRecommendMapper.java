package com.example.travlediary.repository.recommend;

import com.example.travlediary.dto.SeasonDestinationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DestinationRecommendMapper {

    /**
     * 특정 시즌(봄/여름/가을/겨울) + 카테고리(태그)별 여행지 리스트
     * @param season "SPRING", "SUMMER", "FALL", "WINTER"
     * @param categoryId 카테고리 PK (null이면 전체)
     */
    List<SeasonDestinationDto> findBySeasonAndCategory(@Param("season") String season,
                                                       @Param("categoryId") Long categoryId,
                                                       @Param("limit") int limit);

    /**
     * 특정 시즌 전체(카테고리 무관) 추천 여행지 리스트 (limit N개)
     */
    List<SeasonDestinationDto> findBySeason(@Param("season") String season,
                                            @Param("limit") int limit);

    /**
     * 카테고리 PK로 카테고리명 조회 (뱃지 이름 등)
     */
    String findCategoryNameById(@Param("categoryId") Long categoryId);
}
