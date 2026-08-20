package com.example.travlediary.service.kto;

public record KtoDownloadedPhoto(
        String localImageUrl,
        String sourceImageUrl,
        String contentType,
        long fileSize
) {
}
