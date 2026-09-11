package com.example.travlediary.service.kto;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.DestinationTranslationForm;
import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.ShopInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * TourAPI 상세조회 결과를 여행지 등록 폼으로 옮긴다.
 * 단건 자동입력이 화면에서 채우는 것과 같은 칸을 채우고, 값 판별(문자열 boolean, 홈페이지 URL 등)은
 * 이미 {@link KtoTourService} 가 끝낸 상태다.
 */
@Component
public class KtoTourDestinationFormMapper {

    /** TourAPI 는 계절 정보를 주지 않는다. 관리자가 나중에 고치도록 사계절로 둔다. */
    private static final DestinationSeason DEFAULT_SEASON = DestinationSeason.ALL_SEASONS;

    private static final int NAME_MAX_LENGTH = 255;
    private static final int CLOSED_DAYS_MAX_LENGTH = 500;
    private static final int OPENING_HOURS_MAX_LENGTH = 1000;
    private static final int CONTACT_NUMBER_MAX_LENGTH = 255;
    private static final int HOMEPAGE_URL_MAX_LENGTH = 255;

    /**
     * @param regionId 주소 매칭으로 찾은 우리 country_categories 지역 번호
     * @throws KtoTourBulkImportException 지원하지 않는 콘텐츠 유형이거나 여행지명이 없을 때
     */
    public DestinationForm toForm(KtoTourAutofillResponse detail, Long regionId) {
        KtoTourImportContentType contentType = KtoTourImportContentType
                .fromContentTypeId(detail.contentTypeId())
                .orElseThrow(() -> new KtoTourBulkImportException("여행지로 등록할 수 없는 관광 콘텐츠 유형입니다."));
        String name = truncate(detail.title(), NAME_MAX_LENGTH);
        if (name == null) {
            throw new KtoTourBulkImportException("여행지명을 확인할 수 없습니다.");
        }

        DestinationForm form = new DestinationForm();
        form.setType(contentType.destinationType());
        form.setSeason(DEFAULT_SEASON.name());
        form.setRegionId(regionId);
        form.setLatitude(decimal(detail.latitude()));
        form.setLongitude(decimal(detail.longitude()));

        List<DestinationTranslationForm> translations = DestinationForm.newTranslationSlots();
        DestinationTranslationForm korean = translations.get(0);
        korean.setName(name);
        korean.setDescription(detail.overview());
        // 간단 설명은 TourAPI 에 대응하는 값이 없어 비워 둔다.
        korean.setShortDescription(null);
        form.setTranslations(translations);

        switch (contentType.destinationType()) {
            case ATTRACTION -> form.setAttractionInfo(attractionInfo(detail));
            case ACTIVITY -> form.setActivityInfo(activityInfo(detail));
            case SHOP -> form.setShopInfo(shopInfo(detail));
            default -> throw new KtoTourBulkImportException("여행지로 등록할 수 없는 관광 콘텐츠 유형입니다.");
        }
        return form;
    }

    private AttractionInfo attractionInfo(KtoTourAutofillResponse detail) {
        AttractionInfo info = new AttractionInfo();
        info.setClosedDays(truncate(detail.closedDays(), CLOSED_DAYS_MAX_LENGTH));
        info.setOpeningHours(truncate(detail.openingHours(), OPENING_HOURS_MAX_LENGTH));
        info.setAdmissionFee(detail.admissionFee());
        info.setParkingAvailable(detail.parkingAvailable());
        info.setContactNumber(truncate(detail.contactNumber(), CONTACT_NUMBER_MAX_LENGTH));
        info.setHomepageUrl(truncate(detail.homepageUrl(), HOMEPAGE_URL_MAX_LENGTH));
        info.setGuide(detail.guide());
        return info;
    }

    private ActivityInfo activityInfo(KtoTourAutofillResponse detail) {
        ActivityInfo info = new ActivityInfo();
        info.setOpeningHours(truncate(detail.openingHours(), OPENING_HOURS_MAX_LENGTH));
        info.setAdmissionFee(detail.admissionFee());
        info.setReservation(detail.reservation());
        info.setParkingAvailable(detail.parkingAvailable());
        info.setContactNumber(truncate(detail.contactNumber(), CONTACT_NUMBER_MAX_LENGTH));
        info.setHomepageUrl(truncate(detail.homepageUrl(), HOMEPAGE_URL_MAX_LENGTH));
        info.setGuide(detail.guide());
        return info;
    }

    private ShopInfo shopInfo(KtoTourAutofillResponse detail) {
        ShopInfo info = new ShopInfo();
        info.setClosedDays(truncate(detail.closedDays(), CLOSED_DAYS_MAX_LENGTH));
        info.setOpeningHours(truncate(detail.openingHours(), OPENING_HOURS_MAX_LENGTH));
        info.setParkingAvailable(detail.parkingAvailable());
        info.setContactNumber(truncate(detail.contactNumber(), CONTACT_NUMBER_MAX_LENGTH));
        info.setHomepageUrl(truncate(detail.homepageUrl(), HOMEPAGE_URL_MAX_LENGTH));
        info.setGuide(detail.guide());
        return info;
    }

    /** 좌표를 읽을 수 없으면 등록을 막지 않고 좌표만 비운다. */
    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
