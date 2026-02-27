package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PostListDto {
    private Long id;               // 게시글 PK
    private String postType;       // 말머리(게시판 타입)
    private String title;          // 제목
    private int commentCount;      // 댓글 수
    private String nickname;       // 작성자 닉네임
    private String createdAt;      // 작성일 (String or LocalDateTime)
    private int views;             // 조회수
}
