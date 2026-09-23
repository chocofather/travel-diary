package com.example.travlediary.dto.kto;

import java.time.LocalDate;

public record KtoFestivalSearchItemResponse(
        String contentId,
        String title,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        String firstImage,
        String firstImage2,
        String address,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String categoryName,
        String registrationStatus,
        Long registeredFestivalId
) {
    public KtoFestivalSearchItemResponse(String contentId, String title, LocalDate eventStartDate,
                                         LocalDate eventEndDate, String firstImage, String firstImage2,
                                         String address, String lclsSystm1, String lclsSystm2,
                                         String lclsSystm3, String categoryName) {
        this(contentId, title, eventStartDate, eventEndDate, firstImage, firstImage2, address,
                lclsSystm1, lclsSystm2, lclsSystm3, categoryName, "UNKNOWN", null);
    }

    public KtoFestivalSearchItemResponse withRegistration(String status, Long festivalId) {
        return new KtoFestivalSearchItemResponse(contentId, title, eventStartDate, eventEndDate,
                firstImage, firstImage2, address, lclsSystm1, lclsSystm2, lclsSystm3,
                categoryName, status, festivalId);
    }
}
