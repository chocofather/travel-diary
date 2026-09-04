package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * restaurant_info 의 언어별 자유 텍스트.
 *
 * <p>전화번호·홈페이지·좌석 수·가능 여부처럼 언어와 상관없는 값은 여기 담지 않는다.
 * (관광명소 {@link AttractionInfoTranslation} 과 같은 구조)
 */
@Data
@NoArgsConstructor
public class RestaurantInfoTranslation {
    private Long id;
    private Long destinationId;
    private String languageCode;
    private String mainMenu;        // 대표메뉴
    private String priceRange;      // 가격대
    private String openingHours;    // 영업시간
    private String breakTime;       // 브레이크 타임
    private String closedDays;      // 휴무일
    private String etc;             // 기타 안내사항
}
