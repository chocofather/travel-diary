package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryElement {

    private Long id;
    private Long libraryItemId;
    private Integer snapshotVersion;
    private String elementType;
    private String textContent;
    private String imageUrl;
    private String styleType;
    private String colorType;
    private String photoStyle;
    private String textFont;
    private String textColor;
    private DiaryCoverLibraryPhotoShareMode photoShareMode;
    private Long photoAssetId;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal rotation;
    private Integer zIndex;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    /**
     * 화면이 쓰는 그림 주소. DB 칸이 아니라 조회 서비스가 채운다.
     *
     * <p>공유 사진은 예전처럼 통제된 asset 주소가 담기고, 공용 asset 인 스티커는 저장 경로가
     * 곧 공개 주소다. 개인 다이어리 요소와 같은 표지 조각을 함께 쓰기 위한 같은 이름이다.
     */
    private String viewUrl;

    public String getStickerKind() {
        return DiaryStickerKind.of(imageUrl);
    }

    public String getNoteStyleClass() {
        return DiaryNoteStyle.cssClassOf(styleType);
    }

    public String getNoteColorClass() {
        return DiaryNoteColor.cssClassOf(DiaryNoteColor.resolve(colorType));
    }

    public String getPhotoStyleClass() {
        return DiaryCoverPhotoStyle.toCssClass(photoStyle);
    }

    public String getTextFontClass() {
        return DiaryLabelFont.cssClassOf(textFont);
    }
}
