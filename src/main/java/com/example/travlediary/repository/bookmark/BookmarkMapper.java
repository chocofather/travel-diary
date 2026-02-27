package com.example.travlediary.repository.bookmark;

import com.example.travlediary.model.Bookmark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

@Mapper
public interface BookmarkMapper {

    // 북마크 추가 (통합)
    void insert(Bookmark bookmark);

    // 북마크 삭제 (통합)
    void delete(@Param("userId") Long userId,
                @Param("targetType") String targetType,
                @Param("targetId") Long targetId);

    // 북마크 존재 여부 확인 (통합)
    Bookmark findByUserAndTarget(@Param("userId") Long userId,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") Long targetId);

    // 특정 대상(여행지/게시글/코스 등)별 북마크 수
    int countByTarget(@Param("targetType") String targetType,
                      @Param("targetId") Long targetId);

    // 유저가 북마크한 특정 타입(예: 여행지) ID 목록 반환
    Set<Long> findBookmarkedTargetIdsByUserId(@Param("userId") Long userId,
                                              @Param("targetType") String targetType);

    // (원한다면) 유저가 북마크한 모든 대상 타입+ID 조회
    // List<Bookmark> findAllByUserId(Long userId);
}

