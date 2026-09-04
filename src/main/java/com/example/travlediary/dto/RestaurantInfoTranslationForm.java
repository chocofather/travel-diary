package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 식당 상세정보의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 {@code restaurantInfo.*} 입력을 그대로 쓰고, 여기에는 나머지 언어만 담는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 */
@Data
@NoArgsConstructor
public class RestaurantInfoTranslationForm {
    private String languageCode;
    private String mainMenu;
    private String priceRange;
    private String openingHours;
    private String breakTime;
    private String closedDays;
    private String etc;

    public RestaurantInfoTranslationForm(String languageCode) {
        this.languageCode = languageCode;
    }
}
