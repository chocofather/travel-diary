package com.example.travlediary.dto.kto;

public record KtoEnglishTourCandidateResponse(
        String contentId,
        String contentTypeId,
        String title,
        String longitude,
        String latitude,
        double distanceMeters
) {
}
