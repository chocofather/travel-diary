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
