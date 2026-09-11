package com.example.travlediary.service.kto;

import java.sql.Timestamp;

/**
 * 내려받기까지 끝나고 저장만 남은 여행지 이미지 한 장.
 *
 * @param sourceType  이미지 유입 경로. null 이면 관광사진 갤러리(KTO_PHOTO_GALLERY)로 본다.
 * @param licenseType 라이선스 유형. null 이면 관광사진 갤러리 기본값(KOGL_TYPE_1)으로 본다.
 */
public record PreparedKtoPhoto(
        String localImageUrl,
        String sourceImageUrl,
        String externalContentId,
        String title,
        String photographer,
        boolean isMain,
        Timestamp licenseCheckedAt,
        String sourceType,
        String licenseType
) {
    /** 관광사진 갤러리 경로는 출처와 라이선스가 고정이라 두 칸을 넘기지 않는다. */
    public PreparedKtoPhoto(String localImageUrl,
                            String sourceImageUrl,
                            String externalContentId,
                            String title,
                            String photographer,
                            boolean isMain,
                            Timestamp licenseCheckedAt) {
        this(localImageUrl, sourceImageUrl, externalContentId, title, photographer, isMain,
                licenseCheckedAt, null, null);
    }
}
