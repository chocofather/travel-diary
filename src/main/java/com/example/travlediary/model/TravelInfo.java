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
    private TravelInfoScope scope; // 국내/해외 범위
    private TravelInfoContentType contentType; // 일반/축제 정보 구분
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Long categoryId; // 카테고리번호
    private Integer views; // 조회수
    private Long userId; // 회원번호
}
