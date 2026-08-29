package com.example.travlediary.model;

import java.util.Locale;

/**
 * 커스텀 표지에 붙인 사진 한 장의 모습. (diary_cover_*_elements.photo_style)
 *
 * <p>PHOTO 전용이다. STICKER / NOTE / TEXT 는 이 값을 쓰지 않고 늘 비어 있다.
 * NOTE 의 style_type 과는 별개의 칸이다. (그 칸을 사진 용도로 돌려쓰지 않는다)
 */
public enum DiaryCoverPhotoStyle {

    /** 흰 프레임 없이 요소 자리를 사진이 꽉 채운다. 새로 붙이는 사진의 기본값이다. */
    FULL("일반"),
    /** 지금 쓰고 있는 흰색 폴라로이드 프레임. */
    POLAROID("폴라로이드");

    /**
     * 저장된 값이 없을 때 보는 모습.
     *
     * <p>이 칸이 생기기 전에 붙여 둔 사진은 값이 비어 있다. 그 사진들이 예전 모습 그대로
     * 보이도록 폴라로이드로 읽는다. (읽을 때만 그렇게 보고, DB 값을 채워 넣지는 않는다)
     */
    public static final DiaryCoverPhotoStyle LEGACY_DEFAULT = POLAROID;
    /** 화면에 붙는 class 이름의 앞머리. 사진 모습 규칙은 전부 이 이름 아래에만 둔다. */
    private static final String CLASS_PREFIX = "is-photo-";

    private final String label;

    DiaryCoverPhotoStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 저장 값 그대로. (버튼 value 로 쓴다) */
    public String getCode() {
        return name();
    }

    /** 이 모습의 class 이름. (FULL → is-photo-full) */
    public String getCssClass() {
        return toCssClass(name());
    }

    /**
     * 저장된 문자열을 화면이 쓸 class 로 바꾼다.
     * 값이 없거나 모르는 값이면 폴라로이드로 본다. (예전에 붙인 사진이 그대로 보이게 한다)
     */
    public static String toCssClass(String code) {
        return CLASS_PREFIX + of(code).name().toLowerCase(Locale.ROOT);
    }

    /** 저장된 문자열이 가리키는 모습. 값이 없거나 모르는 값이면 폴라로이드다. */
    public static DiaryCoverPhotoStyle of(String code) {
        if (code == null) {
            return LEGACY_DEFAULT;
        }
        for (DiaryCoverPhotoStyle style : values()) {
            if (style.name().equals(code.strip())) {
                return style;
            }
        }
        return LEGACY_DEFAULT;
    }

    /** 허용 값인지 확인한다. (그 밖의 값은 저장 단계에서 막는다) */
    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        for (DiaryCoverPhotoStyle style : values()) {
            if (style.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
