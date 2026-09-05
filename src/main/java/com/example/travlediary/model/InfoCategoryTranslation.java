package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 정보 카테고리 이름의 언어별 값.
 * 여행정보(GENERAL)와 축제·행사(FESTIVAL) 카테고리가 같은 테이블을 함께 쓴다.
 */
@Data
@NoArgsConstructor
public class InfoCategoryTranslation {
    private Long id;
    private Long infoCategoryId;
    private String languageCode;
    private String name;
}
