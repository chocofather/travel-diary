package com.example.travlediary.controller.admin;

import java.util.Map;

/**
 * 관리자 번역 탭에 찍는 언어 이름.
 *
 * <p>여행정보와 축제·행사가 같은 번역 조각을 쓰므로 라벨도 한 곳에서 가져간다.
 * 관리자 화면은 항상 한국어로 그린다.
 */
public final class AdminTranslationLabels {

    /** 번역 패널 접근성 라벨. */
    public static final Map<String, String> LANGUAGE_LABELS = Map.of(
            "en", "영어",
            "ja", "일본어",
            "zh-CN", "중국어(간체)",
            "zh-TW", "중국어(번체)"
    );

    /** 번역 언어 탭 버튼에 찍는 이름. 좁은 자리라 짧게 쓴다. */
    public static final Map<String, String> TAB_LABELS = Map.of(
            "en", "영어",
            "ja", "일본어",
            "zh-CN", "간체",
            "zh-TW", "번체"
    );

    private AdminTranslationLabels() {
    }
}
