package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.model.RestaurantInfoTranslation;
import com.example.travlediary.repository.info.RestaurantInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공개 식당 상세의 자유 텍스트만 화면 언어로 바꾼다.
 *
 * <p>값은 필드마다 따로 고르고, 번역은 여행지 하나당 한 번만 읽는다.
 * 전화번호·홈페이지·좌석 수·가능 여부는 언어와 상관없이 그대로다.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantInfoServiceLocalizedTest {

    @Mock
    private RestaurantInfoMapper restaurantInfoMapper;

    @InjectMocks
    private RestaurantInfoService restaurantInfoService;

    private RestaurantInfo base;

    @BeforeEach
    void setUpBase() {
        base = new RestaurantInfo();
        base.setDestinationId(21L);
        base.setMainMenu("비빔밥");
        base.setPriceRange("1만원~2만원");
        base.setOpeningHours("11:00~21:00");
        base.setBreakTime("15:00~17:00");
        base.setClosedDays("매주 월요일");
        base.setEtc("단체 예약 가능");
        base.setParkingAvailable(true);
        base.setPetAllowed(false);
        base.setSeatCount(48);
        base.setTakeoutAvailable(true);
        base.setDeliveryAvailable(false);
        base.setReservation(true);
        base.setContactNumber("02-1234-5678");
        base.setHomepageUrl("https://example.com");
    }

    @ParameterizedTest
    @CsvSource({
            "KOREAN, 비빔밥",
            "ENGLISH, Bibimbap",
            "JAPANESE, ビビンバ",
            "CHINESE_SIMPLIFIED, 拌饭",
            "CHINESE_TRADITIONAL, 拌飯"
    })
    void eachSupportedLanguageReadsItsOwnText(SupportedLanguage language, String expectedMainMenu) {
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                translation(1L, "ko", "비빔밥", "1만원~2만원", "11:00~21:00",
                        "15:00~17:00", "매주 월요일", "단체 예약 가능"),
                translation(2L, "en", "Bibimbap", "KRW 10,000-20,000", "11:00-21:00",
                        "15:00-17:00", "Every Monday", "Group reservations available"),
                translation(3L, "ja", "ビビンバ", "1万~2万ウォン", "11:00~21:00",
                        "15:00~17:00", "毎週月曜日", "団体予約可"),
                translation(4L, "zh-CN", "拌饭", "1万~2万韩元", "11:00~21:00",
                        "15:00~17:00", "每周一", "可团体预约"),
                translation(5L, "zh-TW", "拌飯", "1萬~2萬韓元", "11:00~21:00",
                        "15:00~17:00", "每週一", "可團體預約")));

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(21L, language);

        assertThat(localized.getMainMenu()).isEqualTo(expectedMainMenu);
        // 번역은 여행지당 한 번만 읽는다 (필드마다 따로 읽지 않는다)
        verify(restaurantInfoMapper, times(1)).findTranslationsByDestinationId(21L);
    }

    @Test
    void missingRequestedLanguageFallsBackToKoreanPerField() {
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                translation(1L, "ko", "비빔밥", "1만원~2만원", "11:00~21:00",
                        "15:00~17:00", "매주 월요일", "단체 예약 가능"),
                // 영어는 대표메뉴와 휴무일만 있다
                translation(2L, "en", "Bibimbap", null, "   ", null, "Every Monday", null)));

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(
                21L, SupportedLanguage.ENGLISH);

        assertThat(localized.getMainMenu()).isEqualTo("Bibimbap");
        assertThat(localized.getClosedDays()).isEqualTo("Every Monday");
        // 비어 있는 칸만 한국어로 내려간다
        assertThat(localized.getPriceRange()).isEqualTo("1만원~2만원");
        assertThat(localized.getOpeningHours()).isEqualTo("11:00~21:00");
        assertThat(localized.getBreakTime()).isEqualTo("15:00~17:00");
        assertThat(localized.getEtc()).isEqualTo("단체 예약 가능");
    }

    @Test
    void withoutRequestedAndKoreanTheFirstRemainingLanguageIsUsedPerField() {
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                translation(3L, "zh-CN", "拌饭", null, null, null, null, null),
                translation(2L, "en", null, "KRW 10,000-20,000", null, null, null, null)));

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(
                21L, SupportedLanguage.JAPANESE);

        // 언어 코드 순서로 결정한다 (en < zh-CN). 필드마다 따로 고른다.
        assertThat(localized.getPriceRange()).isEqualTo("KRW 10,000-20,000");
        assertThat(localized.getMainMenu()).isEqualTo("拌饭");
        // 어느 번역에도 없으면 원문이 남는다
        assertThat(localized.getOpeningHours()).isEqualTo("11:00~21:00");
    }

    @Test
    void withoutAnyTranslationTheBaseValuesStay() {
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of());

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(
                21L, SupportedLanguage.ENGLISH);

        assertThat(localized.getMainMenu()).isEqualTo("비빔밥");
        assertThat(localized.getPriceRange()).isEqualTo("1만원~2만원");
        assertThat(localized.getOpeningHours()).isEqualTo("11:00~21:00");
        assertThat(localized.getBreakTime()).isEqualTo("15:00~17:00");
        assertThat(localized.getClosedDays()).isEqualTo("매주 월요일");
        assertThat(localized.getEtc()).isEqualTo("단체 예약 가능");
    }

    @Test
    void emptyBaseAndTranslationLeaveTheFieldNull() {
        base.setBreakTime("   ");
        base.setEtc(null);
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                translation(1L, "ko", "비빔밥", null, null, null, null, null)));

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(
                21L, SupportedLanguage.ENGLISH);

        assertThat(localized.getBreakTime()).isNull();
        assertThat(localized.getEtc()).isNull();
    }

    @Test
    void languageIndependentValuesAreNeverTouched() {
        givenBase();
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                translation(2L, "en", "Bibimbap", "KRW 10,000-20,000", "11:00-21:00",
                        "15:00-17:00", "Every Monday", "Group reservations available")));

        RestaurantInfo localized = restaurantInfoService.findLocalizedByDestinationId(
                21L, SupportedLanguage.ENGLISH);

        assertThat(localized.getDestinationId()).isEqualTo(21L);
        assertThat(localized.getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        assertThat(localized.getSeatCount()).isEqualTo(48);
        assertThat(localized.getParkingAvailable()).isTrue();
        assertThat(localized.getPetAllowed()).isFalse();
        assertThat(localized.getTakeoutAvailable()).isTrue();
        assertThat(localized.getDeliveryAvailable()).isFalse();
        assertThat(localized.getReservation()).isTrue();
        // 원본 객체는 그대로 둔다 (관리자 화면이 같은 값을 다시 읽어도 안전하다)
        assertThat(base.getMainMenu()).isEqualTo("비빔밥");
    }

    @Test
    void missingRestaurantInfoReturnsNullWithoutReadingTranslations() {
        when(restaurantInfoMapper.findByDestinationId(99L)).thenReturn(null);

        assertThat(restaurantInfoService.findLocalizedByDestinationId(
                99L, SupportedLanguage.ENGLISH)).isNull();
        verify(restaurantInfoMapper, never()).findTranslationsByDestinationId(99L);
    }

    @Test
    void theBaseReadUsedByAdminScreensStaysUntranslated() {
        givenBase();

        assertThat(restaurantInfoService.findByDestinationId(21L)).isSameAs(base);
        verify(restaurantInfoMapper, never()).findTranslationsByDestinationId(21L);
    }

    private void givenBase() {
        when(restaurantInfoMapper.findByDestinationId(21L)).thenReturn(base);
    }

    private RestaurantInfoTranslation translation(Long id, String languageCode, String mainMenu,
                                                  String priceRange, String openingHours,
                                                  String breakTime, String closedDays, String etc) {
        RestaurantInfoTranslation translation = new RestaurantInfoTranslation();
        translation.setId(id);
        translation.setDestinationId(21L);
        translation.setLanguageCode(languageCode);
        translation.setMainMenu(mainMenu);
        translation.setPriceRange(priceRange);
        translation.setOpeningHours(openingHours);
        translation.setBreakTime(breakTime);
        translation.setClosedDays(closedDays);
        translation.setEtc(etc);
        return translation;
    }
}
