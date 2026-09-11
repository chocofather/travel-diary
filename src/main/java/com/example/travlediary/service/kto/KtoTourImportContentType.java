package com.example.travlediary.service.kto;

import com.example.travlediary.model.DestinationType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 지역별 일괄 가져오기 대상이 되는 TourAPI 관광 콘텐츠 유형.
 * 여기에 없는 유형(숙박 32, 음식점 39, 축제·행사 15, 여행코스 25)은 후보에서 제외한다.
 * 유형 → DestinationType 매핑도 이 한 곳에서만 정한다.
 */
public enum KtoTourImportContentType {

    TOURIST_SPOT("12", "관광지", DestinationType.ATTRACTION),
    CULTURAL_FACILITY("14", "문화시설", DestinationType.ATTRACTION),
    LEPORTS("28", "레포츠", DestinationType.ACTIVITY),
    SHOPPING("38", "쇼핑", DestinationType.SHOP);

    private final String contentTypeId;
    private final String contentTypeName;
    private final DestinationType destinationType;

    KtoTourImportContentType(String contentTypeId, String contentTypeName,
                             DestinationType destinationType) {
        this.contentTypeId = contentTypeId;
        this.contentTypeName = contentTypeName;
        this.destinationType = destinationType;
    }

    public static Optional<KtoTourImportContentType> fromContentTypeId(String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return Optional.empty();
        }
        String normalized = contentTypeId.strip();
        return Arrays.stream(values())
                .filter(type -> type.contentTypeId.equals(normalized))
                .findFirst();
    }

    public static List<KtoTourImportContentType> supported() {
        return List.of(values());
    }

    public String contentTypeId() {
        return contentTypeId;
    }

    public String contentTypeName() {
        return contentTypeName;
    }

    public DestinationType destinationType() {
        return destinationType;
    }
}
