package com.tripbora.dto.kto;

public record KtoTourSearchItemResponse(
        String contentId,
        String contentTypeId,
        String contentTypeName,
        String title,
        String address,
        String longitude,
        String latitude
) {
}
