package com.example.travlediary.model;

/**
 * 라벨/떡메모지의 색 한 가지. 목록은 resources/json/diary_notes.json 이 관리한다.
 *
 * <p>모양(style_type)과 색(color_type)은 서로 다른 축이다.
 * 같은 모양에 색만 다른 값을 style_type 으로 따로 만들지 않기 위해 나눠 두었다.
 * (모양 20종 × 색 10종을 한 축에 넣으면 코드도 CSS 규칙도 200개가 된다)
 *
 * <p>저장되는 것은 {@code code} 하나뿐이고, 실제 색은 화면이 이 값으로 정한다.
 * 종이·테두리·글자 세 색이 함께 맞아야 하므로 색상값(hex)을 그대로 담지 않는다.
 */
public record DiaryNoteColor(String code, String label) {

    /** 화면에 붙는 class 이름의 앞머리. 색 규칙은 전부 이 이름 아래에만 둔다. */
    private static final String CLASS_PREFIX = "diary-note-color-";

    /** 이 색의 class 이름. 종이 위의 NOTE 와 고르는 자리가 같은 이름을 쓴다. */
    public String cssClass() {
        return cssClassOf(code);
    }

    /**
     * 화면에 붙일 class 이름. (SAGE → diary-note-color-sage)
     *
     * <p>모양 쪽과 같은 규칙이다. 목록에 있는 code 만 저장되지만, 그 값이 그대로
     * class 이름이 되는 자리라 여기서 한 번 더 글자를 가린다.
     *
     * @return 모르는 색이면 null. (class 가 붙지 않아 그 모양의 기본색으로 그려진다)
     */
    public static String cssClassOf(String code) {
        if (code == null || code.isBlank() || !code.matches("[A-Z0-9_]+")) {
            return null;
        }
        return CLASS_PREFIX + code.toLowerCase().replace('_', '-');
    }
}
