package com.example.travlediary.model;

import java.util.Locale;

/**
 * diaries.notebook_type 에 저장하는 다이어리 내부(속지) 타입.
 * 표지(cover_style)와는 다른 축이다. 표지는 목록의 책 겉모습, 이쪽은 펼쳤을 때의 공책 모양이다.
 * DB 컬럼은 그대로 문자열이고, 여기서 허용 값과 화면 표시(이름/CSS 클래스)를 함께 관리한다.
 */
public enum DiaryNotebookType {

    CLASSIC("일반 노트"),
    SPIRAL("스프링 노트");

    private final String label;

    DiaryNotebookType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 저장 값 그대로. (템플릿에서 radio value 로 쓴다) */
    public String getCode() {
        return name();
    }

    /** 읽기/편집 화면의 책 컨테이너에 붙이는 클래스. 예: SPIRAL → diary-book-spiral */
    public String getCssClass() {
        return toCssClass(name());
    }

    /** 저장된 문자열을 그대로 클래스로 바꾼다. (값이 없으면 기본 타입으로 본다) */
    public static String toCssClass(String code) {
        String value = code == null || code.isBlank() ? CLASSIC.name() : code;
        return "diary-book-" + value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** 허용 값인지 확인한다. (그 밖의 값은 저장 단계에서 막는다) */
    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        for (DiaryNotebookType type : values()) {
            if (type.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
