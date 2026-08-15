package com.example.travlediary.repository.moderation;

import com.example.travlediary.dto.ModeratedContentDto;
import com.example.travlediary.model.ContentModeration;
import com.example.travlediary.model.ModerationTargetType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 콘텐츠 조치 전용 매퍼.
 * 사용자 삭제용 기존 매퍼(user_id 조건 포함)는 그대로 두고, 관리자용 SQL만 여기에 모은다.
 */
@Mapper
public interface ContentModerationMapper {

    /* ---------- 대상 콘텐츠 조회 ---------- */

    /** 아직 노출 중인(deleted = 0) 대상의 작성자 id. 없으면 null. */
    Long findActiveTargetOwnerId(@Param("targetType") ModerationTargetType targetType,
                                 @Param("targetId") Long targetId);

    /** 숨김 상태(deleted = 1)인 대상의 작성자 id. 없으면 null. */
    Long findHiddenTargetOwnerId(@Param("targetType") ModerationTargetType targetType,
                                 @Param("targetId") Long targetId);

    /* ---------- 대상 콘텐츠 숨김/복구 ---------- */

    /** 관리자 숨김. 사용자 삭제와 달리 user_id 조건이 없다. */
    int hideTarget(@Param("targetType") ModerationTargetType targetType,
                   @Param("targetId") Long targetId);

    int restoreTarget(@Param("targetType") ModerationTargetType targetType,
                      @Param("targetId") Long targetId);

    /* ---------- 조치 이력 ---------- */

    int insert(ContentModeration moderation);

    ContentModeration findActiveByTarget(@Param("targetType") ModerationTargetType targetType,
                                         @Param("targetId") Long targetId);

    ContentModeration findActiveByTargetForUpdate(
            @Param("targetType") ModerationTargetType targetType,
            @Param("targetId") Long targetId);

    List<ContentModeration> findByTarget(@Param("targetType") ModerationTargetType targetType,
                                         @Param("targetId") Long targetId);

    /* ---------- 조치된 콘텐츠 관리 목록 ---------- */

    long countModeratedContents(@Param("targetType") ModerationTargetType targetType,
                                @Param("keyword") String keyword);

    List<ModeratedContentDto> findModeratedContents(
            @Param("targetType") ModerationTargetType targetType,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit);

    int restoreModeration(@Param("id") Long id,
                          @Param("restoredAt") LocalDateTime restoredAt,
                          @Param("restoredBy") Long restoredBy,
                          @Param("restoreReason") String restoreReason);
}
