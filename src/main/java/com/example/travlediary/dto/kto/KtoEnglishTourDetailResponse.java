package com.example.travlediary.dto.kto;

/**
 * 영문 관광정보 상세.
 *
 * <p>title/overview 는 detailCommon2, 식당 값들은 detailIntro2 에서 온다.
 * detailIntro2 가 없거나 값이 비면 그 자리는 null 이며 화면은 빈 칸을 그대로 둔다.
 */
public record KtoEnglishTourDetailResponse(
        String title,
        String overview,
        String mainMenu,
        String openingHours,
        String closedDays
) {
}
