package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class Destination {
    private Long id; // 여행지번호
    private BigDecimal latitude; // 위도
    private BigDecimal longitude; // 경도
    private Timestamp createdAt; // 생성일
    private Integer views; // 조회수
    private DestinationSeason season; // 시즌
    private Long userID; // 회원번호
    private Long regionId; // 지역 카테고리 번호
    private DestinationType type;

    // ✅ 추가: 다국어 표시용
    private String name; // destinationTranslation join용
    private String description; // destinationTranslation join용
    private String shortDescription; // destinationTranslation join용
    private String regionName; // country_categories.region_name 조인 결과 담기용

    private String thumbnailPath; // 메인 이미지 URL
    // Destination.java
    private List<DestinationImage> images = new ArrayList<>();




}
