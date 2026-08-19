package com.example.travlediary.dto;

import java.util.List;

/**
 * 일기장형 목록 정렬 기준.
 * 화면/주소에서 오는 값은 반드시 이 목록 안에서만 고르고(모르는 값은 기본값),
 * 실제 ORDER BY 는 Mapper XML 의 &lt;choose&gt; 가 정한다. (SQL 문자열을 밖에서 받지 않는다)
 */
public enum DiarySort {

    UPDATED_DESC("최근 수정순"),
    TRIP_DESC("최근 여행순"),
    TRIP_ASC("오래된 여행순"),
    TITLE_ASC("제목순");

    /** 아무것도 고르지 않았을 때. (주소에서도 이 값은 생략한다) */
    public static final DiarySort DEFAULT = UPDATED_DESC;

    private final String label;

    DiarySort(String label) {
        this.label = label;
    }

    /** 정렬 고르기에 그대로 적는 이름 */
    public String getLabel() {
        return label;
    }

    /** 고를 수 있는 정렬 (화면에 보이는 순서) */
    public static List<DiarySort> options() {
        return List.of(values());
    }

    /** 주소/폼에서 온 값. 비었거나 목록에 없으면 기본 정렬로 본다. */
    public static DiarySort of(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String name = value.strip().toUpperCase();
        for (DiarySort sort : values()) {
            if (sort.name().equals(name)) {
                return sort;
            }
        }
        return DEFAULT;
    }
}
