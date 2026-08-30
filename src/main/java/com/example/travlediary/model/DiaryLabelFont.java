package com.example.travlediary.model;

/**
 * 라벨기(TEXT 요소)에서 고를 수 있는 글꼴 한 가지.
 * 목록은 resources/json/diary_label_fonts.json 이 관리한다.
 *
 * <p>저장되는 것은 {@code code} 하나뿐이고, 그 값이 곧 화면의 글꼴 class 가 된다.
 * code 는 diary-fonts.css 의 글꼴 key 를 그대로 쓴다 (소문자-하이픈).
 * 그래서 code 를 class 로 바꾸는 표를 따로 두지 않는다.
 * (diary_pages.page_header_font 와 같은 key 체계다)
 *
 * <p>{@code label} 은 고르는 자리에 보여 줄 이름이라 저장하지 않는다.
 */
public record DiaryLabelFont(String code, String label) {

    /** 화면에 붙는 class 이름의 앞머리. 글꼴 규칙은 전부 이 이름 아래에만 둔다. */
    private static final String CLASS_PREFIX = "diary-font-";

    /** 이 글꼴의 class. 붙인 라벨과 고르는 자리의 미리보기가 같은 이름을 쓴다. */
    public String fontClass() {
        return cssClassOf(code);
    }

    /**
     * 저장된 값을 화면 class 로 바꾼다. (nanum-square → diary-font-nanum-square)
     *
     * <p>저장되는 값은 목록에 있는 code 뿐이지만(Service 가 확인한다), 그 값이 그대로
     * class 이름이 되는 자리라 여기서 한 번 더 글자를 가린다.
     * 손으로 넣은 행처럼 목록 밖의 값이 섞여 들어와도 엉뚱한 class 로 새지 않는다.
     *
     * @return 모르는 글꼴이면 null. (class 자체가 붙지 않아 기본 글꼴로 그려진다)
     */
    public static String cssClassOf(String code) {
        if (code == null || code.isBlank() || !code.matches("[a-z0-9-]+")) {
            return null;
        }
        return CLASS_PREFIX + code;
    }
}
