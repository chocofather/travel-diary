package com.example.travlediary.repository.search;

import com.example.travlediary.dto.DestinationSearchResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DestinationSearchMapper {
    List<DestinationSearchResultDto> searchDestinations(@Param("keyword") String keyword, @Param("limit") int limit);
}
