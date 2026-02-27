package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ActivityInfo {
    private Long destinationId;      // destinations.id (PK, FK)
    private String openingHours;     // 운영 시간
    private String requiredTime;     // 소요 시간
    private String admissionFee;     // 참가비/이용요금
    private String ageLimit;         // 연령 제한
    private Boolean reservation;     // 사전 예약 필요 여부
    private Boolean equipmentIncluded; // 장비 대여/포함 여부
    private Boolean parkingAvailable;// 주차 가능 여부
    private String contactNumber;    // 연락처
    private String homepageUrl;      // 홈페이지(선택)
    private String guide;            // 이용 안내/기타
}
