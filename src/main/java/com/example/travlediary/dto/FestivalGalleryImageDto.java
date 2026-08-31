package com.example.travlediary.dto;

import com.example.travlediary.model.InfoImage;

public final class FestivalGalleryImageDto {

    private static final String KTO_SOURCE_TYPE = "KTO_TOURAPI";

    private final String imageUrl;
    private final boolean main;
    private final Integer orderIndex;
    private final String sourceType;
    private final String sourceName;
    private final String sourceTitle;
    private final String licenseLabel;

    public FestivalGalleryImageDto(InfoImage image) {
        this.imageUrl = text(image == null ? null : image.getImageUrl());
        this.main = image != null && Boolean.TRUE.equals(image.getIsMain());
        this.orderIndex = image == null ? null : image.getOrderIndex();
        this.sourceType = text(image == null ? null : image.getSourceType());
        this.sourceName = text(image == null ? null : image.getSourceName());
        this.sourceTitle = text(image == null ? null : image.getSourceTitle());
        this.licenseLabel = licenseLabel(image == null ? null : image.getLicenseType());
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isMain() {
        return main;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getLicenseLabel() {
        return licenseLabel;
    }

    public boolean isTourApiImage() {
        return KTO_SOURCE_TYPE.equals(sourceType);
    }

    public boolean isAttributionPresent() {
        return sourceName != null || licenseLabel != null;
    }

    private static String licenseLabel(String licenseType) {
        String normalized = text(licenseType);
        if ("KOGL_TYPE_1".equals(normalized)) {
            return "공공누리 제1유형";
        }
        if ("KOGL_TYPE_3".equals(normalized)) {
            return "공공누리 제3유형";
        }
        return null;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
