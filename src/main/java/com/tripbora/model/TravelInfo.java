package com.tripbora.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class TravelInfo {
    private Long id; // 여행정보 번호
    private String title; // 여행정보 제목
    private String content; // 여행정보 내용 (STRUCTURED 면 검색/SEO 용 파생 HTML)
    private TravelInfoContentFormat contentFormat = TravelInfoContentFormat.QUILL; // 본문 작성 방식
    private String structuredContent; // 구조화 블록 원본 JSON (QUILL 은 null)
    private TravelInfoScope scope; // 국내/해외 범위
    private TravelInfoContentType contentType; // 일반/축제 정보 구분
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Long categoryId; // 카테고리번호
    private Integer views; // 조회수
    private Boolean homeFeatured; // 메인 추천 노출 여부 (GENERAL / GUIDE 만)
    private Integer homeFeaturedOrder; // 메인 추천 노출 순서
    private Long userId; // 회원번호
}
