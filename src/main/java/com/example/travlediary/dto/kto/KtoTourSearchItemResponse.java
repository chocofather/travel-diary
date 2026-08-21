package com.example.travlediary.dto.kto;

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
