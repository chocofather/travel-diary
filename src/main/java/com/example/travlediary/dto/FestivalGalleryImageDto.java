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
    /** 화면 문구가 아니라 안정적인 코드다. 표시할 이름은 화면이 messages 에서 고른다. */
    private final String licenseCode;

    public FestivalGalleryImageDto(InfoImage image) {
        this.imageUrl = text(image == null ? null : image.getImageUrl());
        this.main = image != null && Boolean.TRUE.equals(image.getIsMain());
        this.orderIndex = image == null ? null : image.getOrderIndex();
        this.sourceType = text(image == null ? null : image.getSourceType());
        this.sourceName = text(image == null ? null : image.getSourceName());
        this.sourceTitle = text(image == null ? null : image.getSourceTitle());
        this.licenseCode = licenseCode(image == null ? null : image.getLicenseType());
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

    public String getLicenseCode() {
        return licenseCode;
    }

    public boolean isTourApiImage() {
        return KTO_SOURCE_TYPE.equals(sourceType);
    }

    public boolean isAttributionPresent() {
        return sourceName != null || licenseCode != null;
    }

    /** 화면에 이름을 지어 주는 대신, 아는 라이선스인지만 판별해 코드를 넘긴다. */
    private static String licenseCode(String licenseType) {
        String normalized = text(licenseType);
        return "KOGL_TYPE_1".equals(normalized) || "KOGL_TYPE_3".equals(normalized)
                ? normalized : null;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
