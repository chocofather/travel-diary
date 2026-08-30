package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * diary_elements 한 행. 페이지에 올린 요소 하나다.
 * element_type 이 TEXT 면 text_content 와 text_font 만, PHOTO/STICKER 면 image_url 만 사용한다.
 * (사진 한 장 = 한 행)
 * NOTE(라벨·떡메모지)는 text_content 와 style_type 을 함께 쓰고 image_url 은 쓰지 않는다.
 * PHOTO 와 STICKER 는 같은 자유배치 이미지 요소로, 위치/크기/회전/겹침 순서 컬럼을 똑같이 쓴다.
 * 위치/크기는 페이지 크기 기준 0~1 상대값이다.
 */
@Data
@NoArgsConstructor
public class DiaryElement {

    private Long id; // 요소 번호 (PK)
    private Long pageId; // 페이지 번호
    private String elementType; // 요소 유형 (TEXT | PHOTO | STICKER)
    private String textContent; // 본문 (TEXT / NOTE 전용)
    private String imageUrl; // 이미지 경로 (PHOTO/STICKER 전용)
    private String styleType; // 라벨/메모지 모양 (NOTE 전용, 그 밖의 유형은 null)
    private String colorType; // 라벨/메모지 색 (NOTE 전용, 없으면 기본색 IVORY 로 본다)
    private String photoStyle; // 사진의 모습 (PHOTO 전용, 없으면 폴라로이드로 본다)
    private String textFont; // 글씨 글꼴 (TEXT 전용, 없으면 기본 글꼴)
    private String textColor; // 글씨 색 #RRGGBB (TEXT 전용, 없으면 기본 먹색)
    private BigDecimal positionX; // 가로 위치 (페이지 기준 상대값)
    private BigDecimal positionY; // 세로 위치 (페이지 기준 상대값)
    private BigDecimal width; // 너비 (페이지 기준 상대값)
    private BigDecimal height; // 높이 (페이지 기준 상대값)
    private BigDecimal rotation; // 회전 각도
    private Integer zIndex; // 겹침 순서
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일

    /**
     * 스티커의 성격. 이미 붙여 둔 요소도 저장된 image_url 만 보고 같은 값을 얻는다.
     * (마스킹테이프는 길이만 늘려도 두께가 따라 커지지 않게 화면에서 다르게 다룬다)
     */
    public String getStickerKind() {
        return DiaryStickerKind.of(imageUrl);
    }

    /**
     * 라벨/떡메모지의 모양 class. (DATE_LABEL → diary-note-date-label)
     * 스티커의 성격 값과 같은 자리다 — 저장된 값 하나만 보고 화면이 쓸 이름을 얻는다.
     */
    public String getNoteStyleClass() {
        return DiaryNoteStyle.cssClassOf(styleType);
    }

    /**
     * 라벨/떡메모지의 색 class. (SAGE → diary-note-color-sage)
     * 색이 없는 행은 기본색(IVORY)으로 본다 — 색 칸이 생기기 전에 만든 행도 같은 모습이 된다.
     * (DB 값을 고쳐 쓰지는 않는다)
     */
    public String getNoteColorClass() {
        return DiaryNoteColor.cssClassOf(DiaryNoteColor.resolve(colorType));
    }

    /**
     * 사진의 모습 class. (FULL → is-photo-full)
     * 값이 비어 있으면 폴라로이드로 본다 — 이 칸이 생기기 전에 붙인 사진이 예전 모습 그대로다.
     * (표지 사진과 같은 값·같은 규칙을 쓴다)
     */
    public String getPhotoStyleClass() {
        return DiaryCoverPhotoStyle.toCssClass(photoStyle);
    }

    /** 지금 고른 사진의 모습. 버튼의 눌림 상태를 그릴 때 쓴다. */
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
