package com.tripbora.service.destinationimport;

import com.tripbora.dto.DestinationForm;
import com.tripbora.model.DestinationType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON 일괄등록 계약의 필드 규격. 검증(Validator)과 AI용 마스터 데이터 내보내기가 이 한 곳을 같이 본다.
 *
 * <p>길이는 docs/db/tripbora_schema_reference.md 의 컬럼 정의를 그대로 옮긴 값이다.
 * VARCHAR 는 글자 수(code point), TEXT 는 UTF-8 바이트로 센다.
 * RESTAURANTS 와 CAFE 는 같은 restaurant_info 를 쓴다.</p>
 */
public final class DestinationImportFields {

    public static final int VERSION = 1;
    public static final int MAX_DESTINATIONS = 30;
    public static final int MAX_BYTES = 1_048_576;

    /** destinations 배열 순서와 같은 언어 순서. 한국어가 원본이다. */
    public static final String KOREAN = "ko";
    public static final List<String> LANGUAGES = List.of(KOREAN, "en", "ja", "zh-CN", "zh-TW");
    /** 운영정보 번역 언어. 한국어는 info 원문이 그대로 ko 번역이 된다. */
    public static final List<String> INFO_TRANSLATION_LANGUAGES = DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES;

    /** destination_translations.name / short_description: VARCHAR(255) */
    public static final int NAME_MAX_LENGTH = 255;
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 255;
    /** TEXT 컬럼 한도(UTF-8 바이트). */
    public static final int TEXT_MAX_BYTES = 65_535;
    /** destinations.google_place_id: VARCHAR(255) */
    public static final int GOOGLE_PLACE_ID_MAX_LENGTH = 255;
    public static final int KEY_MAX_LENGTH = 64;
    public static final int EVIDENCE_URL_MAX_LENGTH = 2_000;

    public enum Kind {
        /** VARCHAR(maxLength) */
        TEXT,
        /** TEXT(65,535 바이트) */
        LONG_TEXT,
        /** http/https 주소, VARCHAR(maxLength) */
        URL,
        BOOLEAN,
        /** 0 이상의 정수 (INT) */
        INTEGER,
        /** 0.0~5.0, 소수 첫째 자리까지 (DECIMAL(2,1)) */
        RATING
    }

    /**
     * 유형별 운영정보 필드 하나.
     *
     * @param maxLength    TEXT·URL 은 글자 수, LONG_TEXT 는 바이트 수. 그 밖에는 0
     * @param translatable info.translations 에 넣을 수 있는지
     * @param fact         실제 확인이 필요한 사실값인지(AI 가 추측하면 안 된다)
     */
    public record Field(String name, Kind kind, int maxLength, boolean translatable, boolean fact) {
    }

    private static final Map<DestinationType, List<Field>> INFO_FIELDS = new EnumMap<>(DestinationType.class);

    static {
        INFO_FIELDS.put(DestinationType.ATTRACTION, List.of(
                text("openingHours", 1000, true, true),
                text("closedDays", 500, true, true),
                longText("admissionFee", true, true),
                flag("parkingAvailable"),
                text("contactNumber", 255, false, true),
                url("homepageUrl"),
                longText("guide", true, false)));
        INFO_FIELDS.put(DestinationType.ACCOMMODATION, List.of(
                text("checkinTime", 10, false, true),
                text("checkoutTime", 10, false, true),
                new Field("roomCount", Kind.INTEGER, 0, false, true),
                text("roomType", 255, true, true),
                new Field("starRating", Kind.RATING, 0, false, true),
                flag("breakfastIncluded"),
                flag("parkingAvailable"),
                flag("petAllowed"),
                text("contactNumber", 32, false, true),
                url("homepageUrl"),
                text("etc", 255, true, false)));
        List<Field> restaurant = List.of(
                text("mainMenu", 64, true, true),
                text("priceRange", 32, true, true),
                text("openingHours", 64, true, true),
                text("breakTime", 32, true, true),
                text("closedDays", 32, true, true),
                flag("parkingAvailable"),
                flag("petAllowed"),
                new Field("seatCount", Kind.INTEGER, 0, false, true),
                flag("takeoutAvailable"),
                flag("deliveryAvailable"),
                flag("reservation"),
                text("contactNumber", 32, false, true),
                url("homepageUrl"),
                text("etc", 255, true, false));
        INFO_FIELDS.put(DestinationType.RESTAURANTS, restaurant);
        INFO_FIELDS.put(DestinationType.CAFE, restaurant);
        INFO_FIELDS.put(DestinationType.ACTIVITY, List.of(
                text("openingHours", 1000, true, true),
                text("requiredTime", 32, true, true),
                longText("admissionFee", true, true),
                text("ageLimit", 32, true, true),
                flag("reservation"),
                flag("equipmentIncluded"),
                flag("parkingAvailable"),
                text("contactNumber", 255, false, true),
                url("homepageUrl"),
                longText("guide", true, false)));
        INFO_FIELDS.put(DestinationType.SHOP, List.of(
                text("closedDays", 500, true, true),
                text("openingHours", 1000, true, true),
                text("mainProducts", 255, true, true),
                flag("parkingAvailable"),
                text("contactNumber", 255, false, true),
                url("homepageUrl"),
                longText("guide", true, false)));
    }

    private DestinationImportFields() {
    }

    public static List<Field> infoFields(DestinationType type) {
        return type == null ? List.of() : INFO_FIELDS.getOrDefault(type, List.of());
    }

    /** 이름 → 필드. 순서는 계약 순서다. */
    public static Map<String, Field> infoFieldMap(DestinationType type) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : infoFields(type)) {
            fields.put(field.name(), field);
        }
        return fields;
    }

    /** 여행지 공통 필드 중 사실값. 값이 있는데 evidence 가 없으면 경고한다. TourAPI contentId 는 미리보기에서 직접 확인하므로 뺀다. */
    public static final Set<String> COMMON_FACT_FIELDS = Set.of("latitude", "longitude", "wikidataQid", "googlePlaceId");

    private static Field text(String name, int maxLength, boolean translatable, boolean fact) {
        return new Field(name, Kind.TEXT, maxLength, translatable, fact);
    }

    private static Field longText(String name, boolean translatable, boolean fact) {
        return new Field(name, Kind.LONG_TEXT, TEXT_MAX_BYTES, translatable, fact);
    }

    private static Field url(String name) {
        return new Field(name, Kind.URL, 255, false, true);
    }

    private static Field flag(String name) {
        return new Field(name, Kind.BOOLEAN, 0, false, true);
    }
}
