package com.example.travlediary.dto.kto;

public record KtoTourAutofillResponse(
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String longitude,
        String latitude,
        String overview,
        String homepageUrl,
        String contactNumber,
        String closedDays,
        String openingHours,
        String admissionFee,
        String guide
) {
}
