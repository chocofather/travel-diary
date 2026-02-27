package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AttractionInfo {
    private Long destinationId;   // FK + PK (DB에서 int면 Integer)
    private String closedDays; // 휴관일
    private String openingHours; // 운영시간
    private String admissionFee; // 입장료
    private Boolean parkingAvailable; // 주차 가능 여부
    private String contactNumber; // 전화번호
    private String homepageUrl; // 홈체이지
    private String guide; // 관람 안내/기타
}
