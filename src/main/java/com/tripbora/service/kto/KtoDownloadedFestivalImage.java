package com.tripbora.service.kto;

public record KtoDownloadedFestivalImage(
        String localImageUrl,
        String sourceImageUrl,
        String contentType,
        long fileSize
) {
}
