package com.tripbora.service.destinationimport;

import com.tripbora.dto.AccommodationInfoTranslationForm;
import com.tripbora.dto.ActivityInfoTranslationForm;
import com.tripbora.dto.AttractionInfoTranslationForm;
import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.DestinationTranslationForm;
import com.tripbora.dto.RestaurantInfoTranslationForm;
import com.tripbora.dto.ShopInfoTranslationForm;
import com.tripbora.dto.destinationimport.DestinationImportItem;
import com.tripbora.model.AccommodationInfo;
import com.tripbora.model.ActivityInfo;
import com.tripbora.model.AttractionInfo;
import com.tripbora.model.RestaurantInfo;
import com.tripbora.model.ShopInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 검증·매핑을 마친 JSON 한 건을 기존 저장 경로가 받는 {@link DestinationForm} 으로 바꾼다.
 * 등록폼이 보내는 모양(번역 슬롯 순서, 유형별 정보 객체, 편의시설 목록)을 그대로 따른다.
 * 이미지·사진 선택·Wikipedia 출처는 채우지 않는다.
 */
@Component
public class DestinationImportFormMapper {

    /**
     * @param ktoContentId 국내 TourAPI contentId(검증을 마친 값). 없으면 null
     * @param wikidataQid  해외 Wikidata QID(대문자로 맞춘 값). 없으면 null
     */
    public DestinationForm toForm(DestinationImportItem item, DestinationImportMasterResolver.Resolution resolution,
                                  String ktoContentId, String wikidataQid) {
        DestinationForm form = new DestinationForm();
        form.setType(item.type());
        form.setSeason(item.season().name());
        form.setRegionId(resolution.regionId());
        form.setLatitude(item.latitude());
        form.setLongitude(item.longitude());
        form.setGooglePlaceId(item.external().googlePlaceId());
        form.setKtoContentId(ktoContentId);
        form.setWikidataQid(wikidataQid);
        form.setTranslations(translations(item));
        form.setCategoryIds(new ArrayList<>(resolution.categoryIds()));
        // 대표를 JSON 에 적지 않았으면 비워 두어 저장이 기존 규칙(가장 작은 ID)으로 고르게 한다.
        form.setMainCategoryId(resolution.requestedMainCategoryId());
        List<Integer> amenityIds = new ArrayList<>(resolution.amenityIds());
        DestinationImportItem.Info info = item.info();
        switch (item.type()) {
            case ATTRACTION -> {
                form.setAttractionInfo(attraction(info));
                form.setAttractionInfoTranslations(attractionTranslations(info));
                form.setAttractionAmenityIds(amenityIds);
            }
            case ACCOMMODATION -> {
                form.setAccommodationInfo(accommodation(info));
                form.setAccommodationInfoTranslations(accommodationTranslations(info));
                form.setAccommodationAmenityIds(amenityIds);
            }
            case RESTAURANTS, CAFE -> {
                form.setRestaurantInfo(restaurant(info));
                form.setRestaurantInfoTranslations(restaurantTranslations(info));
                form.setRestaurantAmenityIds(amenityIds);
            }
            case ACTIVITY -> {
                form.setActivityInfo(activity(info));
                form.setActivityInfoTranslations(activityTranslations(info));
                form.setActivityAmenityIds(amenityIds);
            }
            case SHOP -> {
                form.setShopInfo(shop(info));
                form.setShopInfoTranslations(shopTranslations(info));
                form.setShopAmenityIds(amenityIds);
            }
        }
        return form;
    }

    /** 등록폼과 같은 슬롯(ko, en, ja, zh-CN, zh-TW). 빈 칸은 등록폼이 보내는 것처럼 빈 문자열이다. */
    private List<DestinationTranslationForm> translations(DestinationImportItem item) {
        List<DestinationTranslationForm> slots = DestinationForm.newTranslationSlots();
        for (DestinationTranslationForm slot : slots) {
            DestinationImportItem.Text text = item.translation(slot.getLanguageCode());
            if (text == null) continue;
            slot.setName(emptyIfNull(text.name()));
            slot.setShortDescription(emptyIfNull(text.shortDescription()));
            slot.setDescription(emptyIfNull(text.description()));
        }
        return slots;
    }

    private AttractionInfo attraction(DestinationImportItem.Info info) {
        AttractionInfo value = new AttractionInfo();
        value.setOpeningHours(info.text("openingHours"));
        value.setClosedDays(info.text("closedDays"));
        value.setAdmissionFee(info.text("admissionFee"));
        value.setParkingAvailable(info.bool("parkingAvailable"));
        value.setContactNumber(info.text("contactNumber"));
        value.setHomepageUrl(info.text("homepageUrl"));
        value.setGuide(info.text("guide"));
        return value;
    }

    private List<AttractionInfoTranslationForm> attractionTranslations(DestinationImportItem.Info info) {
        List<AttractionInfoTranslationForm> slots = DestinationForm.newAttractionInfoTranslationSlots();
        for (AttractionInfoTranslationForm slot : slots) {
            String language = slot.getLanguageCode();
            slot.setOpeningHours(info.translated(language, "openingHours"));
            slot.setClosedDays(info.translated(language, "closedDays"));
            slot.setAdmissionFee(info.translated(language, "admissionFee"));
            slot.setGuide(info.translated(language, "guide"));
        }
        return slots;
    }

    private AccommodationInfo accommodation(DestinationImportItem.Info info) {
        AccommodationInfo value = new AccommodationInfo();
        value.setCheckinTime(info.text("checkinTime"));
        value.setCheckoutTime(info.text("checkoutTime"));
        value.setRoomCount(info.integer("roomCount"));
        value.setRoomType(info.text("roomType"));
        value.setStarRating(info.decimal("starRating") == null ? null : info.decimal("starRating").doubleValue());
        value.setBreakfastIncluded(info.bool("breakfastIncluded"));
        value.setParkingAvailable(info.bool("parkingAvailable"));
        value.setPetAllowed(info.bool("petAllowed"));
        value.setContactNumber(info.text("contactNumber"));
        value.setHomepageUrl(info.text("homepageUrl"));
        value.setEtc(info.text("etc"));
        return value;
    }

    private List<AccommodationInfoTranslationForm> accommodationTranslations(DestinationImportItem.Info info) {
        List<AccommodationInfoTranslationForm> slots = DestinationForm.newAccommodationInfoTranslationSlots();
        for (AccommodationInfoTranslationForm slot : slots) {
            String language = slot.getLanguageCode();
            slot.setRoomType(info.translated(language, "roomType"));
            slot.setEtc(info.translated(language, "etc"));
        }
        return slots;
    }

    private RestaurantInfo restaurant(DestinationImportItem.Info info) {
        RestaurantInfo value = new RestaurantInfo();
        value.setMainMenu(info.text("mainMenu"));
        value.setPriceRange(info.text("priceRange"));
        value.setOpeningHours(info.text("openingHours"));
        value.setBreakTime(info.text("breakTime"));
        value.setClosedDays(info.text("closedDays"));
        value.setParkingAvailable(info.bool("parkingAvailable"));
        value.setPetAllowed(info.bool("petAllowed"));
        value.setSeatCount(info.integer("seatCount"));
        value.setTakeoutAvailable(info.bool("takeoutAvailable"));
        value.setDeliveryAvailable(info.bool("deliveryAvailable"));
        value.setReservation(info.bool("reservation"));
        value.setContactNumber(info.text("contactNumber"));
        value.setHomepageUrl(info.text("homepageUrl"));
        value.setEtc(info.text("etc"));
        return value;
    }

    private List<RestaurantInfoTranslationForm> restaurantTranslations(DestinationImportItem.Info info) {
        List<RestaurantInfoTranslationForm> slots = DestinationForm.newRestaurantInfoTranslationSlots();
        for (RestaurantInfoTranslationForm slot : slots) {
            String language = slot.getLanguageCode();
            slot.setMainMenu(info.translated(language, "mainMenu"));
            slot.setPriceRange(info.translated(language, "priceRange"));
            slot.setOpeningHours(info.translated(language, "openingHours"));
            slot.setBreakTime(info.translated(language, "breakTime"));
            slot.setClosedDays(info.translated(language, "closedDays"));
            slot.setEtc(info.translated(language, "etc"));
        }
        return slots;
    }

    private ActivityInfo activity(DestinationImportItem.Info info) {
        ActivityInfo value = new ActivityInfo();
        value.setOpeningHours(info.text("openingHours"));
        value.setRequiredTime(info.text("requiredTime"));
        value.setAdmissionFee(info.text("admissionFee"));
        value.setAgeLimit(info.text("ageLimit"));
        value.setReservation(info.bool("reservation"));
        value.setEquipmentIncluded(info.bool("equipmentIncluded"));
        value.setParkingAvailable(info.bool("parkingAvailable"));
        value.setContactNumber(info.text("contactNumber"));
        value.setHomepageUrl(info.text("homepageUrl"));
        value.setGuide(info.text("guide"));
        return value;
    }

    private List<ActivityInfoTranslationForm> activityTranslations(DestinationImportItem.Info info) {
        List<ActivityInfoTranslationForm> slots = DestinationForm.newActivityInfoTranslationSlots();
        for (ActivityInfoTranslationForm slot : slots) {
            String language = slot.getLanguageCode();
            slot.setOpeningHours(info.translated(language, "openingHours"));
            slot.setRequiredTime(info.translated(language, "requiredTime"));
            slot.setAdmissionFee(info.translated(language, "admissionFee"));
            slot.setAgeLimit(info.translated(language, "ageLimit"));
            slot.setGuide(info.translated(language, "guide"));
        }
        return slots;
    }

    private ShopInfo shop(DestinationImportItem.Info info) {
        ShopInfo value = new ShopInfo();
        value.setClosedDays(info.text("closedDays"));
        value.setOpeningHours(info.text("openingHours"));
        value.setMainProducts(info.text("mainProducts"));
        value.setParkingAvailable(info.bool("parkingAvailable"));
        value.setContactNumber(info.text("contactNumber"));
        value.setHomepageUrl(info.text("homepageUrl"));
        value.setGuide(info.text("guide"));
        return value;
    }

    private List<ShopInfoTranslationForm> shopTranslations(DestinationImportItem.Info info) {
        List<ShopInfoTranslationForm> slots = DestinationForm.newShopInfoTranslationSlots();
        for (ShopInfoTranslationForm slot : slots) {
            String language = slot.getLanguageCode();
            slot.setClosedDays(info.translated(language, "closedDays"));
            slot.setOpeningHours(info.translated(language, "openingHours"));
            slot.setMainProducts(info.translated(language, "mainProducts"));
            slot.setGuide(info.translated(language, "guide"));
        }
        return slots;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
