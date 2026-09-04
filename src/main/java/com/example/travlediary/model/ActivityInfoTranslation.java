package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * activity_info 의 언어별 자유 텍스트.
 *
 * <p>사전 예약·장비 포함·주차 여부, 연락처, 홈페이지처럼
 * 언어와 상관없는 값은 여기 담지 않는다.
 */
@Data
@NoArgsConstructor
public class ActivityInfoTranslation {
    private Long id;
    private Long destinationId;
    private String languageCode;
    private String openingHours;  // 운영 시간
    private String requiredTime;  // 소요 시간
    private String admissionFee;  // 참가비/이용요금
    private String ageLimit;      // 연령 제한
    private String guide;         // 이용 안내/기타
}
