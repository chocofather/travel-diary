package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class CourseCommentDto {
    private Long id;
    private Long courseId;
    private Long parentCommentId;
    private Long replyToCommentId;
    private String replyToNickname;
    /** 답글 대상이 최종 탈퇴 회원이면 true. @멘션도 익명 닉네임 대신 공통 문구로 내려간다. */
    private boolean replyToWithdrawn;
    private boolean replyToDeleted;
    private String content;
    private String sourceLanguage;
    private boolean translationAvailable;
    private String writerNickname;
    /** 최종 탈퇴(DEACTIVATED) 회원이면 true. 공개 프로필이 없으므로 링크도 걸지 않는다. */
    private boolean writerWithdrawn;
    private Long writerUserId;
    private String writerProfileImage;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean deleted;
    /** 관리자 조치로 숨겨진 댓글이면 true. 사용자가 직접 지운 댓글과 구분한다. */
    private boolean moderated;
    /** 첨부 사진(최대 3장) URL. 사진이 없거나 삭제·조치된 댓글이면 빈 목록이다. */
    private List<String> imageUrls = List.of();
    private long likeCount;
    private boolean likedByMe;
    private boolean myComment;
}
