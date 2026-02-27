package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccommodationInfo {
    private Long destinationId;      // destinations.id (PK, FK)
    private String checkinTime;      // 체크인 시간 (예: "15:00")
    private String checkoutTime;     // 체크아웃 시간 (예: "11:00")
    private Integer roomCount;       // 객실 수
    private String roomType;         // 객실 유형 (예: "싱글, 트윈, 더블")
    private Double starRating;       // 숙소 등급 (예: 4.5)
    private Boolean breakfastIncluded;   // 조식 포함 여부
    private Boolean parkingAvailable;    // 주차 가능 여부
    private Boolean petAllowed;          // 반려동물 가능 여부
    private String contactNumber;        // 숙소 연락처
    private String homepageUrl;          // 숙소 홈페이지(선택)
    private String etc;                  // 기타 안내사항(선택)
}
