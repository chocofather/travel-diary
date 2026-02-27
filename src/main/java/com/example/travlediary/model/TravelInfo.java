package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class TravelInfo {
    private Long id; // 여행정보 번호
    private String title; // 여행정보 제목
    private String content; // 여행정보 내용
    private Timestamp createTime; // 생성일
    private Timestamp updateTime; // 수정일
    private Long categoryId; // 카테고리번호
    private Long userId; // 회원번호
}
