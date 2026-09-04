package com.example.travlediary.dto.kto;

public record KtoForeignTourCandidateResponse(
        String contentId,
        String contentTypeId,
        String title,
        String longitude,
        String latitude,
        double distanceMeters
) {
}
