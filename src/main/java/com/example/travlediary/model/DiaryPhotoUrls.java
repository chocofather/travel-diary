package com.example.travlediary.model;

/**
 * 개인 다이어리 사진을 브라우저에 내려줄 때 쓰는 통제된 주소.
 *
 * <p>DB 의 {@code image_url} 은 private 저장소를 가리키는 저장 키일 뿐이라 화면에 그대로
 * 나가서는 안 된다. 화면과 JSON 응답이 쓰는 주소는 모두 여기에서만 만든다 —
 * 템플릿마다 문자열을 잇지 않게 하려는 것이다.
 *
 * <p>표지 라이브러리 공유 사진은 이 자리와 무관하다. 그쪽은 예전부터 자기 통제된 주소
 * ({@code /diaries/cover-library/assets/{assetId}})를 쓰고 그대로 둔다.
 */
public final class DiaryPhotoUrls {

    /** 라이브러리 공유 사진의 통제된 주소 앞머리. 기존 구조를 그대로 쓴다. */
    public static final String LIBRARY_ASSET_PREFIX = "/diaries/cover-library/assets/";

    private DiaryPhotoUrls() {
    }

    /** 다이어리 대표 이미지 */
    public static String coverImage(Long diaryId) {
        return "/diaries/" + diaryId + "/cover-image";
    }

    /** 페이지에 붙인 사진 한 장 */
    public static String pageElementPhoto(Long diaryId, Long pageId, Long elementId) {
        return "/diaries/" + diaryId + "/pages/" + pageId + "/elements/" + elementId + "/photo";
    }

    /** 다이어리에 실제로 적용된 표지의 사진 한 장 */
    public static String coverElementPhoto(Long diaryId, Long elementId) {
        return "/diaries/" + diaryId + "/cover/elements/" + elementId + "/photo";
    }

    /** 보관함 "내 표지 디자인"의 사진 한 장 */
    public static String coverDesignElementPhoto(Long designId, Long elementId) {
        return "/diaries/cover-designs/" + designId + "/elements/" + elementId + "/photo";
    }

    /** 라이브러리 공유 사진 */
    public static String libraryAsset(Long assetId) {
        return LIBRARY_ASSET_PREFIX + assetId;
    }
}
