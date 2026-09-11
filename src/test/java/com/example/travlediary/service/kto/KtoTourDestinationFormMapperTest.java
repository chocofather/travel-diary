package com.example.travlediary.service.kto;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KtoTourDestinationFormMapperTest {

    private final KtoTourDestinationFormMapper mapper = new KtoTourDestinationFormMapper();

    @Test
    void touristSpotBecomesAnAttractionFormWithTheKoreanSlotFilled() {
        DestinationForm form = mapper.toForm(detail("12"), 235L);

        assertThat(form.getType()).isEqualTo(DestinationType.ATTRACTION);
        assertThat(form.getSeason()).isEqualTo(DestinationSeason.ALL_SEASONS.name());
        assertThat(form.getRegionId()).isEqualTo(235L);
        assertThat(form.getLatitude()).isEqualByComparingTo(new BigDecimal("37.579"));
        assertThat(form.getLongitude()).isEqualByComparingTo(new BigDecimal("126.991"));
        assertThat(form.getTranslations().get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(form.getTranslations().get(0).getName()).isEqualTo("창덕궁");
        assertThat(form.getTranslations().get(0).getDescription()).isEqualTo("궁궐 설명");
        assertThat(form.getAttractionInfo().getOpeningHours()).isEqualTo("09:00~18:00");
        assertThat(form.getAttractionInfo().getParkingAvailable()).isTrue();
        assertThat(form.getAccommodationInfo()).isNull();
        assertThat(form.getRestaurantInfo()).isNull();
    }

    @Test
    void leportsAndShoppingUseTheirOwnSubtypeInfo() {
        assertThat(mapper.toForm(detail("28"), 235L).getActivityInfo().getOpeningHours())
                .isEqualTo("09:00~18:00");
        assertThat(mapper.toForm(detail("38"), 235L).getShopInfo().getClosedDays())
                .isEqualTo("월요일");
    }

    @Test
    void stayAndFoodContentTypesAreNotBulkImportTargets() {
        assertThatThrownBy(() -> mapper.toForm(detail("32"), 235L))
                .isInstanceOf(KtoTourBulkImportException.class);
        assertThatThrownBy(() -> mapper.toForm(detail("39"), 235L))
                .isInstanceOf(KtoTourBulkImportException.class);
        assertThatThrownBy(() -> mapper.toForm(detail("15"), 235L))
                .isInstanceOf(KtoTourBulkImportException.class);
    }

    @Test
    void unreadableCoordinatesDoNotBlockTheRegistration() {
        KtoTourAutofillResponse detail = new KtoTourAutofillResponse(
                "126508", "12", "창덕궁", "서울 종로구", "좌표없음", "",
                "궁궐 설명", null, null, null, null, null, null);

        DestinationForm form = mapper.toForm(detail, 235L);

        assertThat(form.getLatitude()).isNull();
        assertThat(form.getLongitude()).isNull();
    }

    private KtoTourAutofillResponse detail(String contentTypeId) {
        return new KtoTourAutofillResponse(
                "126508", contentTypeId, "창덕궁", "서울 종로구", "126.991", "37.579",
                "궁궐 설명", "https://example.test", "02-0000-0000", "월요일",
                "09:00~18:00", "무료", "안내",
                null, null, null, null, null,
                Boolean.TRUE, null, null, null);
    }
}
