package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * diary_cover_elements 한 행. 다이어리에 적용된 표지에 올린 요소 하나다.
 *
 * <p>{@link DiaryCoverDesignElement} 와 같은 구성이고 부모만 다르다. (디자인 → 표지)
 * 적용할 때 원본 디자인의 요소를 하나씩 복사해 만든다.
 */
@Data
@NoArgsConstructor
public class DiaryCoverElement {

    private Long id; // 요소 번호 (PK)
    private Long coverId; // 표지 번호
    private String elementType; // 요소 유형 (PHOTO | STICKER | NOTE | TEXT)
    private String textContent; // 글 (NOTE / TEXT 전용)
    private String imageUrl; // 이미지 경로 (PHOTO/STICKER 전용)
    private String styleType; // 라벨/메모지 모양 (NOTE 전용)
    private String colorType; // 라벨/메모지 색 (NOTE 전용, 없으면 그 모양의 기본색)
    private String photoStyle; // 사진의 모습 (PHOTO 전용, 없으면 폴라로이드로 본다)
    private String textFont; // 글씨 글꼴 (TEXT 전용, 없으면 기본 글꼴)
    private String textColor; // 글씨 색 #RRGGBB (TEXT 전용, 없으면 기본 먹색)
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

    /** 라벨/떡메모지의 색 class. 색이 없는 행은 기본색(IVORY)으로 본다. */
    public String getNoteColorClass() {
        return DiaryNoteColor.cssClassOf(DiaryNoteColor.resolve(colorType));
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

    /**
     * 라벨기로 붙인 글씨의 글꼴 class. (nanum-square → diary-font-nanum-square)
     * 글꼴이 없으면 class 도 없다 — 그때는 기본 글꼴로 그려진다.
     */
    public String getTextFontClass() {
        return DiaryLabelFont.cssClassOf(textFont);
    }
}
