package com.example.travlediary.dto;

import com.example.travlediary.model.*;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class DestinationForm {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String googlePlaceId; // 해외 여행지 Google 지도 마커용. 선택 입력
    private String season;
    @NotNull(message = "지역을 선택해 주세요.")
    private Long regionId;

    private DestinationType type; // enum 직접 받아도 됨 (권장)

    private Long destinationId; // 여행지 기본키

    /**
     * 관리자 화면 번역 슬롯 순서: 0 = 한국어(원본), 1~4 = 번역 탭 언어.
     * 저장·복원은 이 순서가 아니라 각 슬롯의 languageCode 로만 한다.
     */
    private static final String KOREAN = "ko";

    private List<DestinationTranslationForm> translations = newTranslationSlots();

    public static List<DestinationTranslationForm> newTranslationSlots() {
        List<DestinationTranslationForm> slots = new ArrayList<>();
        slots.add(new DestinationTranslationForm(KOREAN, "", "", ""));
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(new DestinationTranslationForm(languageCode, "", "", ""));
        }
        return slots;
    }

    /**
     * 식당/카페 상세정보의 번역 입력 슬롯.
     * 한국어는 {@link #restaurantInfo} 입력이 그대로 base 이자 ko 번역이 되므로 여기에는 없다.
     */
    private List<RestaurantInfoTranslationForm> restaurantInfoTranslations =
            newRestaurantInfoTranslationSlots();

    /**
     * 관광지 상세정보의 번역 입력 슬롯.
     * 한국어는 {@link #attractionInfo} 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<AttractionInfoTranslationForm> attractionInfoTranslations =
            newAttractionInfoTranslationSlots();

    /**
     * 숙박 상세정보의 번역 입력 슬롯.
     * 한국어는 {@link #accommodationInfo} 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<AccommodationInfoTranslationForm> accommodationInfoTranslations =
            newAccommodationInfoTranslationSlots();

    /**
     * 체험/액티비티 상세정보의 번역 입력 슬롯.
     * 한국어는 {@link #activityInfo} 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<ActivityInfoTranslationForm> activityInfoTranslations =
            newActivityInfoTranslationSlots();

    /** 슬롯 순서를 화면과 저장 로직이 함께 쓰는 기준. 언어 코드는 사용자가 정하지 않는다. */
    public static List<RestaurantInfoTranslationForm> newRestaurantInfoTranslationSlots() {
        List<RestaurantInfoTranslationForm> slots = new ArrayList<>();
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(new RestaurantInfoTranslationForm(languageCode));
        }
        return slots;
    }

    public static List<AttractionInfoTranslationForm> newAttractionInfoTranslationSlots() {
        List<AttractionInfoTranslationForm> slots = new ArrayList<>();
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(new AttractionInfoTranslationForm(languageCode));
        }
        return slots;
    }

    public static List<AccommodationInfoTranslationForm> newAccommodationInfoTranslationSlots() {
        List<AccommodationInfoTranslationForm> slots = new ArrayList<>();
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(new AccommodationInfoTranslationForm(languageCode));
        }
        return slots;
    }

    public static List<ActivityInfoTranslationForm> newActivityInfoTranslationSlots() {
        List<ActivityInfoTranslationForm> slots = new ArrayList<>();
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(new ActivityInfoTranslationForm(languageCode));
        }
        return slots;
    }

    /** 유형별 상세정보 번역 슬롯 언어 (한국어는 base 입력이 대신한다). */
    public static final List<String> SUBTYPE_TRANSLATION_LANGUAGES =
            List.of("en", "ja", "zh-CN", "zh-TW");

    private List<Long> categoryIds = new ArrayList<>();

    private boolean main;
    private boolean slide;

    private MultipartFile[] images;
    private String ktoSelectedPhotosJson = "[]";

    private List<Integer> attractionAmenityIds;
    private List<Integer> accommodationAmenityIds;
    private List<Integer> restaurantAmenityIds;
    private List<Integer> activityAmenityIds;
    private List<Integer> shopAmenityIds;

    /* 폼 바인딩 */
    private AttractionInfo attractionInfo; // 여행지 정보
    private AccommodationInfo accommodationInfo; // 숙소 정보
    private RestaurantInfo restaurantInfo; // 식당 & 카페 정보
    private ActivityInfo activityInfo; // 엑티비티 정보
    private ShopInfo shopInfo; // 쇼핑 정보


    public static DestinationForm fromDetailDto(DestinationDetailDto dto, List<DestinationTranslation> translations) {
        DestinationForm form = new DestinationForm();
        if (dto == null || dto.getDestination() == null)
            return form;

        form.setDestinationId(dto.getDestination().getId());

        // 1. 기본 필드 복사
        form.setLatitude(dto.getDestination().getLatitude());
        form.setLongitude(dto.getDestination().getLongitude());
        form.setGooglePlaceId(dto.getDestination().getGooglePlaceId());
        form.setSeason(dto.getDestination().getSeason() != null ? dto.getDestination().getSeason().name() : null);
        form.setRegionId(dto.getDestination().getRegionId());
        form.setType(dto.getDestination().getType());

        // --- 번역값 변환 (조회 순서가 아니라 languageCode 기준으로 슬롯을 채운다) ---
        List<DestinationTranslationForm> slots = new ArrayList<>();
        slots.add(translationSlot(translations, KOREAN));
        for (String languageCode : SUBTYPE_TRANSLATION_LANGUAGES) {
            slots.add(translationSlot(translations, languageCode));
        }
        form.setTranslations(slots);

        // 2. 상세 정보 복사
        form.setAttractionInfo(dto.getAttractionInfo());
        form.setAccommodationInfo(dto.getAccommodationInfo());
        form.setRestaurantInfo(dto.getRestaurantInfo());
        form.setActivityInfo(dto.getActivityInfo());
        form.setShopInfo(dto.getShopInfo());

        // 3. 카테고리 복사
        if (dto.getCategoryIds() != null)
            form.setCategoryIds(dto.getCategoryIds());

        // 4. 편의시설 ID만 복사
        if (dto.getAttractionAmenities() != null)
            form.setAttractionAmenityIds(dto.getAttractionAmenities().stream().map(AmenityDto::getId).toList());
        if (dto.getAccommodationAmenities() != null)
            form.setAccommodationAmenityIds(dto.getAccommodationAmenities().stream().map(AmenityDto::getId).toList());
        if (dto.getRestaurantAmenities() != null)
            form.setRestaurantAmenityIds(dto.getRestaurantAmenities().stream().map(AmenityDto::getId).toList());
        if (dto.getActivityAmenities() != null)
            form.setActivityAmenityIds(dto.getActivityAmenities().stream().map(AmenityDto::getId).toList());
        if (dto.getShopAmenities() != null)
            form.setShopAmenityIds(dto.getShopAmenities().stream().map(AmenityDto::getId).toList());

        return form;
    }

    /**
     * 조회 순서를 신뢰하지 않고 languageCode 가 일치하는 번역만 슬롯에 넣는다.
     * 해당 언어가 없으면 빈 폼을 돌려주어 화면의 index 바인딩이 항상 두 칸을 갖도록 한다.
     */
    private static DestinationTranslationForm translationSlot(List<DestinationTranslation> translations,
                                                             String languageCode) {
        if (translations != null) {
            for (DestinationTranslation translation : translations) {
                if (translation != null && languageCode.equals(translation.getLanguageCode())) {
                    return new DestinationTranslationForm(
                            languageCode,
                            translation.getName(),
                            translation.getDescription(),
                            translation.getShortDescription()
                    );
                }
            }
        }
        return new DestinationTranslationForm(languageCode, "", "", "");
    }

}
