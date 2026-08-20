package com.example.travlediary.model;

/**
 * 스티커의 성격 구분. (DB 유형은 그대로 STICKER 하나뿐이다)
 * 마스킹테이프는 공용 asset 폴더 하나로만 알아보므로, 판정 규칙을 여기 한 곳에만 둔다.
 * picker 목록도, 이미 붙여 둔 요소(저장된 image_url)도 같은 규칙으로 판정된다.
 */
public final class DiaryStickerKind {

    /** 화면(DOM)에 실어 주는 이름. JS/CSS 가 이 값으로 마스킹테이프를 알아본다. */
    public static final String MASKING_TAPE = "masking-tape";

    private static final String MASKING_TAPE_PREFIX =
            "/images/diary/stickers/" + MASKING_TAPE + "/";

    private DiaryStickerKind() {
    }

    public static boolean isMaskingTape(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(MASKING_TAPE_PREFIX);
    }

    /** 화면에 실어 줄 값. 일반 스티커는 따로 표시하지 않는다. (null → 속성 자체가 없다) */
    public static String of(String imageUrl) {
        return isMaskingTape(imageUrl) ? MASKING_TAPE : null;
    }
}
