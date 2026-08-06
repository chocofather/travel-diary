package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class PostComment {
    private Long id; //회원 게시물 댓글번호
    private Long postId; // 회원게시물 번호
    private Long userId; // 회원번호
    private String content; // 내용
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Boolean deleted; // 삭제여부
    private Integer likes; // 좋아요 수
    private Long parentCommentId; // 부모 댓글 번호
    private Long replyToCommentId; // 실제 답글 대상 댓글 번호

}
