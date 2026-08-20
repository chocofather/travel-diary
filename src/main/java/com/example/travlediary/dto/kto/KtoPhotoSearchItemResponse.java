package com.example.travlediary.dto.kto;

public record KtoPhotoSearchItemResponse(
        String externalContentId,
        String title,
        String imageUrl,
        String photographyMonth,
        String photographyLocation,
        String photographer,
        String searchKeyword,
        String createdTime,
        String modifiedTime,
        String sourceType,
        String sourceName,
        String licenseType,
        String licenseLabel
) {
}
