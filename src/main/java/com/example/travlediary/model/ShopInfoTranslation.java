package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * shop_info 의 언어별 자유 텍스트.
 *
 * <p>주차 가능 여부, 연락처, 홈페이지처럼 언어와 상관없는 값은 여기 담지 않는다.
 */
@Data
@NoArgsConstructor
public class ShopInfoTranslation {
    private Long id;
    private Long destinationId;
    private String languageCode;
    private String closedDays;    // 휴점일
    private String openingHours;  // 영업시간
    private String mainProducts;  // 주요상품/카테고리
    private String guide;         // 기타 안내사항
}
