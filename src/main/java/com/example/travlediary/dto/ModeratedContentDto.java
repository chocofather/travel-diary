package com.example.travlediary.dto;

import com.example.travlediary.model.ModerationTargetType;
import lombok.Data;

import java.sql.Timestamp;

/** 관리자 조치(ACTIVE)된 콘텐츠 한 줄. */
@Data
public class ModeratedContentDto {
    private Long moderationId;
    private ModerationTargetType targetType;
    private Long targetId;
    /** 댓글이면 원본 글/코스/여행지 id. 글·코스는 null. */
    private Long parentId;
    private String title;
    private String contentSnippet;
    private Long authorUserId;
    private String authorName;
    private String reason;
    private String adminName;
    private Timestamp createdAt;

    /** 댓글 계열이면 true. 목록에서 글과 댓글을 구분해 표시한다. */
    public boolean isComment() {
        return targetType == ModerationTargetType.POST_COMMENT
                || targetType == ModerationTargetType.COURSE_COMMENT
                || targetType == ModerationTargetType.DESTINATION_COMMENT;
    }
}
