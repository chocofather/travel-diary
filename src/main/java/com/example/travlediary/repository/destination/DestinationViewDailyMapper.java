package com.example.travlediary.repository.destination;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 여행지 일별 조회 집계(destination_view_daily) 쓰기.
 * 세션 중복 판정은 여기서 하지 않는다. 호출한 쪽이 이미 '오늘 처음 본 여행지'로 판정한 경우만 부른다.
 */
@Mapper
public interface DestinationViewDailyMapper {

    /** 여행지 × 날짜 행이 없으면 1로 만들고, 있으면 1 올린다. viewDate 는 Asia/Seoul 기준 날짜다. */
    void incrementDailyView(@Param("destinationId") Long destinationId,
                            @Param("viewDate") LocalDate viewDate);
}
