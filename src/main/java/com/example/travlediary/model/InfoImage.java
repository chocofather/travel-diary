package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class InfoImage {
    private Long id; // 이미지번호
    private String imageUrl; // 이미지 url
    private Integer orderIndex; // 정렬 순서
    private Timestamp createdAt; // 생성일
    private Long infoId; // 정보번호
}
