package com.example.travlediary.model;

import java.util.Locale;

/**
 * diaries.cover_style 에 저장하는 표지 스타일.
 * DB 컬럼은 그대로 문자열이고, 여기서 허용 값과 화면 표시(이름/CSS 클래스)를 함께 관리한다.
 */
public enum DiaryCoverStyle {

    DEFAULT("기본 아이보리"),
    LEATHER_BLACK("가죽 블랙"),
    LEATHER_DARK_BROWN("가죽 다크브라운"),
    LEATHER_LIGHT_BROWN("가죽 라이트브라운"),
    LEATHER_DEEP_GREEN("가죽 딥그린"),
    HARDCOVER_NAVY("양장 네이비"),
    HARDCOVER_BURGUNDY("양장 버건디"),
    HARDCOVER_BEIGE("양장 베이지");

    private final String label;

    DiaryCoverStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 저장 값 그대로. (템플릿에서 radio value 로 쓴다) */
    public String getCode() {
        return name();
    }

    /** 목록/폼에서 함께 쓰는 표지 스타일 클래스. 예: LEATHER_BLACK → diary-cover-leather-black */
    public String getCssClass() {
        return toCssClass(name());
    }

    /** 저장된 문자열을 그대로 클래스로 바꾼다. (목록 DTO 는 문자열만 들고 있다) */
    public static String toCssClass(String code) {
        return "diary-cover-" + code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** 저장된 문자열의 화면 이름. 아는 값이 아니면 기본 표지 이름으로 본다. */
    public static String labelOf(String code) {
        for (DiaryCoverStyle style : values()) {
            if (style.name().equals(code)) {
                return style.label;
            }
        }
        return DEFAULT.label;
    }

    /** 허용 값인지 확인한다. (그 밖의 값은 저장 단계에서 막는다) */
    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        for (DiaryCoverStyle style : values()) {
            if (style.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
