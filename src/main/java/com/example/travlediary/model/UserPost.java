package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class UserPost {
    private Long id; // 회원 게시물 번호
    private String title; // 제목
    private String content; // 내용
    private PostType postType; // 글 타입 여행질문, 팁
    private Integer views; // 조회 수
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Boolean deleted; // 삭제여부
    private Long userId; // 회원번호

}
