package com.example.travlediary.dto.kto;

/**
 * 외국어 관광정보 상세.
 *
 * <p>title/overview 는 detailCommon2, 나머지는 detailIntro2 에서 온다.
 * 유형별로 응답 필드 이름이 다르므로 어느 칸에 넣을 값인지를 기준으로 이름을 붙였다.
 * 한 번에 한 유형만 오므로 유형과 상관없는 칸은 null 이며, 화면은 빈 칸을 그대로 둔다.
 */
public record KtoForeignTourDetailResponse(
        String title,
        String overview,
        /** 관광지·문화시설·음식점·쇼핑의 휴무일. */
        String closedDays,
        /** 관광지·문화시설·음식점·체험·쇼핑의 운영/영업시간. */
        String openingHours,
        /** 관광지·문화시설·체험의 이용요금. */
        String admissionFee,
        /** 음식점 대표메뉴. */
        String mainMenu,
        /** 숙소 객실 유형. */
        String roomType,
        /** 쇼핑 주요 판매품목. */
        String mainProducts
) {
}
