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
        // 음식점(39)
        String mainMenu,
        // 숙박(32)
        String checkinTime,
        String checkoutTime,
        Integer roomCount,
        String roomType,
        // TourAPI 의 문자열 boolean 정보. 판별할 수 없으면 false 로 단정하지 않고 null 로 남긴다.
        Boolean parkingAvailable,
        Boolean takeoutAvailable,
        Boolean reservation,
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
                homepageUrl, contactNumber, closedDays, openingHours, admissionFee, guide,
                null, null, null, null, null, null, null, null, null);
    }

    public KtoTourAutofillResponse withRegionMatch(KtoTourRegionMatchResponse match) {
        return new KtoTourAutofillResponse(
                contentId, contentTypeId, title, address, longitude, latitude, overview,
                homepageUrl, contactNumber, closedDays, openingHours, admissionFee, guide,
                mainMenu, checkinTime, checkoutTime, roomCount, roomType,
                parkingAvailable, takeoutAvailable, reservation, match
        );
    }
}
