package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * accommodation_info 의 언어별 자유 텍스트.
 *
 * <p>체크인·체크아웃 시각, 객실 수, 등급, 여부 값, 연락처, 홈페이지처럼
 * 언어와 상관없는 값은 여기 담지 않는다.
 */
@Data
@NoArgsConstructor
public class AccommodationInfoTranslation {
    private Long id;
    private Long destinationId;
    private String languageCode;
    private String roomType;   // 객실 유형
    private String etc;        // 기타 안내사항
}
