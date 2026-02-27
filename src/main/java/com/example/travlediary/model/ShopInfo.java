package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShopInfo {
    private Long destinationId;     // destinations.id와 1:1 연결 (PK+FK)
    private String closedDays;      // 휴점일 (예: "월요일, 명절")
    private String openingHours;    // 영업시간 (예: "10:00~20:00")
    private String mainProducts;    // 주요상품/카테고리 (예: "의류, 소품, 식품")
    private Boolean parkingAvailable; // 주차 가능 여부
    private String contactNumber;   // 연락처
    private String homepageUrl;     // 홈페이지(선택)
    private String guide;           // 기타 안내사항(선택)
}
