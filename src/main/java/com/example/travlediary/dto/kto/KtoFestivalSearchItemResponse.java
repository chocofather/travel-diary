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
        String categoryName
) {
}
