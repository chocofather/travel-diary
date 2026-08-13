package com.example.travlediary.repository.search;

import com.example.travlediary.dto.GlobalSearchResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GlobalSearchMapper {

    List<GlobalSearchResultDto> search(
            @Param("keywordPattern") String keywordPattern,
            @Param("type") String type,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long count(
            @Param("keywordPattern") String keywordPattern,
            @Param("type") String type);
}
