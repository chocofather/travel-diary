package com.example.travlediary.model;

/**
 * 라벨/떡메모지 디자인 한 가지. 목록은 resources/json/diary_notes.json 이 관리한다.
 *
 * <p>스티커와 달리 그림 파일이 없다. 저장되는 것은 이 {@code code} 하나뿐이고,
 * 실제 모양은 화면이 이 값으로 정한다. (색·크기·글꼴 같은 표시 값은 여기 두지 않는다)
 *
 * <p>{@code category} 는 고르는 자리를 나누기 위한 값이다.
 * 라벨은 한 줄짜리 작은 딱지, 떡메모지는 여러 줄을 적는 메모지다.
 *
 * <p>{@code sample} 은 고르는 자리에서 모양을 알아보게 하는 보기 글이다.
 * 화면에만 쓰고 저장하지 않는다 — 붙인 NOTE 의 글은 언제나 빈 채로 시작한다.
 *
 * <p>{@code defaultColor} 는 색을 고르지 않고 붙였을 때 쓰는 색이다.
 * 색은 모양과 다른 축이라 {@link DiaryNoteColor} 가 따로 관리한다.
 */
public record DiaryNoteStyle(String code, String category, String label, String sample,
                             String defaultColor) {

    /** 날짜·제목처럼 한 줄로 적는 작은 딱지. */
    public static final String CATEGORY_LABEL = "LABEL";
    /** 여러 줄을 적는 메모지. */
    public static final String CATEGORY_MEMO = "MEMO";

    /** 화면에 붙는 class 이름의 앞머리. 모양 규칙은 전부 이 이름 아래에만 둔다. */
    private static final String CLASS_PREFIX = "diary-note-";

    /**
     * 화면에 붙일 class 이름. (DATE_LABEL → diary-note-date-label)
     *
     * <p>저장되는 값은 목록에 있는 code 뿐이지만(Service 가 확인한다), 그 값이 그대로
     * class 이름이 되는 자리라 여기서 한 번 더 글자를 가린다.
     * 손으로 넣은 행처럼 목록 밖의 값이 섞여 들어와도 엉뚱한 class 로 새지 않는다.
     *
     * @return 모르는 모양이면 null. (class 자체가 붙지 않아 기본 모양으로만 그려진다)
     */
    /** 이 디자인의 모양 class. 종이 위의 NOTE 와 고르는 자리의 미리보기가 같은 이름을 쓴다. */
    public String styleClass() {
        return cssClassOf(code);
    }

    public static String cssClassOf(String code) {
        if (code == null || code.isBlank() || !code.matches("[A-Z0-9_]+")) {
            return null;
        }
        return CLASS_PREFIX + code.toLowerCase().replace('_', '-');
    }
}
