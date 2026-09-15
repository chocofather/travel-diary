package com.example.travlediary.model;

/**
 * 공용 스티커 한 개. 관리자가 DB 카탈로그에서 관리한다.
 * 클라이언트는 id 만 보내고 실제 경로(imageUrl)는 서버가 카탈로그에서 고른다.
 *
 * 여러 다이어리가 같은 URL을 저장하므로 요소를 지우거나 카탈로그에서 숨겨도 파일은 남긴다.
 */
public record DiarySticker(String id, String name, String category, String imageUrl,
                           DiaryStickerCollection collection,
                           String tapeType, DiaryStickerRepeat repeat,
                           DiaryStickerType stickerType,
                           DiaryStickerAccessTier accessTier) {

    /** 마스킹테이프 안의 작은 갈래. 적혀 있지 않으면 일반 테이프로 본다. */
    public static final String TAPE_NORMAL = "NORMAL";
    /** 반투명: 바탕 필름이 은은하게 남는다. */
    public static final String TAPE_TRANSLUCENT = "TRANSLUCENT";
    /** 클리어: 바탕 필름이 거의 보이지 않고 무늬만 얹힌다. */
    public static final String TAPE_CLEAR = "CLEAR";

    /** picker 가 넓게 보여줄지 정할 때 쓰는 성격 값. (마스킹테이프 외에는 없음) */
    public String kind() {
        return stickerType == null ? DiaryStickerKind.of(imageUrl) : stickerType.kindCode();
    }

    /** 화면(DOM)에 실어 주는 표현 스타일 값. 분류·테이프 갈래와는 다른 축이다. */
    public String collectionCode() {
        return collection.getCode();
    }

    /** 가운데 무늬를 되풀이해서 그리는 스티커인지. (목록에 조각 경로가 있을 때만) */
    public boolean isRepeating() {
        return repeat != null;
    }
}
