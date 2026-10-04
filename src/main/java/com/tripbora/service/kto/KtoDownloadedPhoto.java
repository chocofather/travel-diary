package com.tripbora.service.kto;

public record KtoDownloadedPhoto(
        String localImageUrl,
        String sourceImageUrl,
        String contentType,
        long fileSize
) {
}
