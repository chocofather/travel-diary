package com.example.travlediary.model;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class CourseComment {
    private Long id; // 댓글 번호
    private Long parentCommentId; // 부모 댓글 번호
    private String content; // 내용
    private String imageUrl; // 이미지
    private Integer likes; // 좋아요 수
    private Boolean deleted; // 삭제 여부 소프트 딜리트
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Long userId; // 회원번호
    private Long courseId; // 코스 번호
}
