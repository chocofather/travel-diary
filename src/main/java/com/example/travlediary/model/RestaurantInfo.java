package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RestaurantInfo {
    private Long destinationId;     // destinations.id (PK, FK)
    private String mainMenu;        // 대표메뉴
    private String priceRange;      // 가격대
    private String openingHours;    // 영업시간
    private String breakTime;       // 브레이크 타임
    private String closedDays;      // 휴무일
    private Boolean parkingAvailable;    // 주차 가능 여부
    private Boolean petAllowed;          // 반려동물 가능 여부
    private Integer seatCount;           // 좌석 수
    private Boolean takeoutAvailable;    // 테이크아웃 가능 여부
    private Boolean deliveryAvailable;   // 배달 가능 여부
    private Boolean reservation;         // 예약 가능 여부
    private String contactNumber;        // 연락처
    private String homepageUrl;          // 홈페이지(선택)
    private String etc;                  // 기타 안내사항(선택)
}
