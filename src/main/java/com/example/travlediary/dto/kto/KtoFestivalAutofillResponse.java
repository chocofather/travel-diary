package com.example.travlediary.dto.kto;

import java.time.LocalDate;

public record KtoFestivalAutofillResponse(
        String contentId,
        String title,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        String firstImage,
        String firstImage2,
        String address,
        String eventPlace,
        String overview,
        String playTime,
        String useTimeFestival,
        String sponsor1,
        String sponsor1Tel,
        String sponsor2,
        String sponsor2Tel,
        String homepage,
        String eventHomepage,
        String tel,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String categoryName
) {
}
