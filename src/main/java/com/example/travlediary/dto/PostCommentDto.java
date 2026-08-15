package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentDto {
    private Long id;
    private Long postId;
    private Long parentCommentId;
    private Long replyToCommentId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String replyToNickname;
    private boolean replyToDeleted;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String content;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String writerNickname;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long writerUserId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String writerProfileImage;
    private Timestamp createdAt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Timestamp updatedAt;
    private boolean deleted;
    /** 관리자 조치로 숨겨진 댓글이면 true. 사용자가 직접 지운 댓글과 구분한다. */
    private boolean moderated;
    private long likeCount;
    private boolean likedByMe;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean myComment;
}
