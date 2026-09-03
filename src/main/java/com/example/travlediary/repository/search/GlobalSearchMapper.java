package com.example.travlediary.repository.search;

import com.example.travlediary.dto.GlobalSearchResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GlobalSearchMapper {

    /** languageCode 는 여행지 번역 검색에 쓰는 canonical 언어 코드. 없으면 한국어 원문만 찾는다. */
    List<GlobalSearchResultDto> search(
            @Param("keywordPattern") String keywordPattern,
            @Param("type") String type,
            @Param("languageCode") String languageCode,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long count(
            @Param("keywordPattern") String keywordPattern,
            @Param("type") String type,
            @Param("languageCode") String languageCode);
}
