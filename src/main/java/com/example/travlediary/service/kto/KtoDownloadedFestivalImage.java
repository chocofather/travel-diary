package com.example.travlediary.service.kto;

public record KtoDownloadedFestivalImage(
        String localImageUrl,
        String sourceImageUrl,
        String contentType,
        long fileSize
) {
}
