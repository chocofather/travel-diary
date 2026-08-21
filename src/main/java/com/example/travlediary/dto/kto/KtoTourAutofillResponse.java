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
        String guide,
        KtoTourRegionMatchResponse regionMatch
) {
    public KtoTourAutofillResponse(
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
        this(contentId, contentTypeId, title, address, longitude, latitude, overview,
                homepageUrl, contactNumber, closedDays, openingHours, admissionFee, guide, null);
    }

    public KtoTourAutofillResponse withRegionMatch(KtoTourRegionMatchResponse match) {
        return new KtoTourAutofillResponse(
                contentId, contentTypeId, title, address, longitude, latitude, overview,
                homepageUrl, contactNumber, closedDays, openingHours, admissionFee, guide, match
        );
    }
}
