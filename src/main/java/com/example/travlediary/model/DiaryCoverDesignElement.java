package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * diary_cover_design_elements 한 행. 저장해 둔 표지 디자인에 올린 요소 하나다.
 *
 * <p>컬럼 구성과 좌표 체계는 {@link DiaryElement}(페이지 다꾸)와 같다.
 * PHOTO/STICKER 는 image_url 만, NOTE 는 text_content 와 style_type 을 함께 쓰고,
 * TEXT 는 text_content 만 쓴다. 위치/크기는 표지 크기 기준 0~1 상대값이다.
 * (같은 모양을 쓰지만 페이지 요소와는 별개의 테이블이다)
 */
@Data
@NoArgsConstructor
public class DiaryCoverDesignElement {

    private Long id; // 요소 번호 (PK)
    private Long designId; // 디자인 번호
    private String elementType; // 요소 유형 (PHOTO | STICKER | NOTE | TEXT)
    private String textContent; // 글 (NOTE / TEXT 전용)
    private String imageUrl; // 이미지 경로 (PHOTO/STICKER 전용)
    private String styleType; // 라벨/메모지 모양 (NOTE 전용)
    private String colorType; // 라벨/메모지 색 (NOTE 전용, 없으면 그 모양의 기본색)
    private String photoStyle; // 사진의 모습 (PHOTO 전용, 없으면 폴라로이드로 본다)
    private BigDecimal positionX; // 가로 위치 (표지 기준 상대값)
    private BigDecimal positionY; // 세로 위치 (표지 기준 상대값)
    private BigDecimal width; // 너비 (표지 기준 상대값)
    private BigDecimal height; // 높이 (표지 기준 상대값)
    private BigDecimal rotation; // 회전 각도
    private Integer zIndex; // 겹침 순서
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일

    /** 스티커의 성격. 저장된 image_url 하나만 보고 페이지 다꾸와 같은 값을 얻는다. */
    public String getStickerKind() {
        return DiaryStickerKind.of(imageUrl);
    }

    /** 라벨/떡메모지의 모양 class. (DATE_LABEL → diary-note-date-label) */
    public String getNoteStyleClass() {
        return DiaryNoteStyle.cssClassOf(styleType);
    }

    /** 라벨/떡메모지의 색 class. 색이 없으면 class 도 없다. */
    public String getNoteColorClass() {
        return DiaryNoteColor.cssClassOf(colorType);
    }

    /**
     * 사진의 모습 class. (FULL → is-photo-full)
     * 값이 비어 있으면 폴라로이드로 본다 — 읽을 때만 그렇게 보고 DB 값은 그대로 둔다.
     */
    public String getPhotoStyleClass() {
        return DiaryCoverPhotoStyle.toCssClass(photoStyle);
    }

    /** 지금 고른 모습. 버튼의 눌림 상태를 그릴 때 쓴다. */
    public String getPhotoStyleCode() {
        return DiaryCoverPhotoStyle.of(photoStyle).getCode();
    }
}
