package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class PostImage {
    private Long id; // 이미지 번호
    private String imageUrl; // 이미지 URL
    private Boolean deleted;
    private Timestamp uploadedAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Long postId; // 글번호
}
