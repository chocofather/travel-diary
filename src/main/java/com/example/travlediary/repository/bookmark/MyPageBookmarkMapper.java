package com.example.travlediary.repository.bookmark;

import com.example.travlediary.dto.MyPageCommunityBookmarkDto;
import com.example.travlediary.dto.MyPageDestinationBookmarkDto;
import com.example.travlediary.dto.MyPageTravelInfoBookmarkDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MyPageBookmarkMapper {

    List<MyPageDestinationBookmarkDto> findDestinationBookmarks(
            @Param("userId") Long userId,
            @Param("scope") String scope,
            @Param("koreaRootId") Long koreaRootId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countDestinationBookmarks(
            @Param("userId") Long userId,
            @Param("scope") String scope,
            @Param("koreaRootId") Long koreaRootId
    );

    List<MyPageCommunityBookmarkDto> findCommunityBookmarks(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countCommunityBookmarks(
            @Param("userId") Long userId,
            @Param("type") String type
    );

    List<MyPageTravelInfoBookmarkDto> findTravelInfoBookmarks(
            @Param("userId") Long userId,
            @Param("scope") String scope,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countTravelInfoBookmarks(
            @Param("userId") Long userId,
            @Param("scope") String scope
    );
}
