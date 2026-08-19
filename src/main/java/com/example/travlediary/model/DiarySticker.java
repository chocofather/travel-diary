package com.example.travlediary.model;

import java.util.Optional;

/**
 * 다이어리에 붙일 수 있는 공용 스티커 목록.
 * 클라이언트는 코드(예: PLANE)만 보내고 실제 경로는 서버가 이 목록에서 고른다.
 * (임의의 외부 이미지 주소를 STICKER 로 저장할 수 없다)
 *
 * 파일은 사용자 업로드가 아니라 사이트 공용 정적 asset 이므로 요소를 지워도 파일은 남긴다.
 */
public enum DiarySticker {

    PLANE("비행기", "plane"),
    SUITCASE("캐리어", "suitcase"),
    CAMERA("카메라", "camera"),
    PASSPORT("여권", "passport"),
    MAP("지도", "map"),
    MAP_PIN("위치", "map-pin"),
    PALM("야자수", "palm"),
    STAR("별", "star"),
    HEART("하트", "heart");

    /** 공용 스티커 asset 디렉터리. (업로드 경로와 섞지 않는다) */
    private static final String ASSET_DIRECTORY = "/images/diary-stickers/";

    private final String label;
    private final String fileName;

    DiarySticker(String label, String fileName) {
        this.label = label;
        this.fileName = fileName;
    }

    public String getLabel() {
        return label;
    }

    /** 저장 값이자 picker 가 보내는 값 */
    public String getCode() {
        return name();
    }

    /** diary_elements.image_url 에 저장할 경로 */
    public String getImageUrl() {
        return ASSET_DIRECTORY + fileName + ".svg";
    }

    /** 아는 스티커일 때만 돌려준다. 그 밖의 값은 저장 단계에서 막는다. */
    public static Optional<DiarySticker> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String trimmed = code.strip();
        for (DiarySticker sticker : values()) {
            if (sticker.name().equals(trimmed)) {
                return Optional.of(sticker);
            }
        }
        return Optional.empty();
    }
}
