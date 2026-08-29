package com.example.travlediary.model;

/**
 * 커스텀 표지에서 고르는 표지 재질.
 *
 * <p>기본 표지 8종은 재질과 색이 한 덩어리로 묶여 있다("가죽 딥그린"처럼).
 * 커스텀 표지는 색을 사용자가 따로 고르므로, 그 8종을 재질 세 갈래로만 보여 준다.
 * 실제로 저장되는 값은 그 갈래의 대표 스타일이고, 색은 background_color 가 맡는다.
 * (DB 는 그대로다. base_cover_style 이 재질을, background_color 가 색을 담당한다)
 *
 * <p>이미 만들어 둔 디자인의 값도 이 갈래로 읽어 준다. 예를 들어 LEATHER_DEEP_GREEN 은
 * "가죽"으로 보이고, 재질을 바꾸지 않는 한 저장된 값 그대로 남는다.
 */
public enum DiaryCoverMaterial {

    /** 무늬 없는 기본 표지 */
    PLAIN("기본", DiaryCoverStyle.DEFAULT),
    /** 가죽 결. 대표 값 하나만 저장하고 색은 따로 고른다. */
    LEATHER("가죽", DiaryCoverStyle.LEATHER_BLACK),
    /** 양장 천 결 */
    HARDCOVER("양장", DiaryCoverStyle.HARDCOVER_NAVY);

    private static final String LEATHER_PREFIX = "LEATHER_";
    private static final String HARDCOVER_PREFIX = "HARDCOVER_";

    private final String label;
    private final DiaryCoverStyle representative;

    DiaryCoverMaterial(String label, DiaryCoverStyle representative) {
        this.label = label;
        this.representative = representative;
    }

    public String getLabel() {
        return label;
    }

    /** 이 재질을 고르면 저장되는 값. (base_cover_style 에 들어간다) */
    public String getCode() {
        return representative.getCode();
    }

    /** 고르는 자리의 견본에 쓰는 class. 실제 표지와 같은 재질로 보이게 한다. */
    public String getCssClass() {
        return representative.getCssClass();
    }

    /** 저장된 표지 스타일이 어느 갈래인지. 모르는 값은 기본으로 본다. */
    public static DiaryCoverMaterial of(String coverStyle) {
        if (coverStyle == null) {
            return PLAIN;
        }
        String style = coverStyle.strip();
        if (style.startsWith(LEATHER_PREFIX)) {
            return LEATHER;
        }
        if (style.startsWith(HARDCOVER_PREFIX)) {
            return HARDCOVER;
        }
        return PLAIN;
    }

    /**
     * 저장할 표지 스타일을 고른다.
     *
     * <p>재질이 그대로면 쓰던 값을 그대로 둔다. "가죽 딥그린"으로 만들어 둔 디자인이
     * 이름만 고쳐 저장했다고 대표 가죽색으로 바뀌지 않게 하려는 것이다.
     * 재질을 실제로 바꿨을 때만 새 갈래의 대표 값으로 옮긴다.
     */
    public static String resolveCoverStyle(String currentStyle, String requestedStyle) {
        DiaryCoverMaterial requested = of(requestedStyle);
        return of(currentStyle) == requested ? currentStyle : requested.getCode();
    }
}
