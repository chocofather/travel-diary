package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AmenityTranslation {
    private Integer id;            // PK (AUTO_INCREMENT)
    private Integer amenityId;     // amenities.id (FK)
    private String languageCode;   // 'ko', 'en', 'ja' 등
    private String name;           // 번역된 편의시설명
    private String code;           // (조인 결과용, 실제 amenity테이블의 code. DB에는 없음)
    private String iconUrl;        // (조인 결과용, amenities.icon_url. NULL 이면 code 기반 .png 로 대체)
}
