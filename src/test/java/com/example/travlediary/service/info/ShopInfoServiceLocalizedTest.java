package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.ShopInfoTranslationForm;
import com.example.travlediary.model.ShopInfo;
import com.example.travlediary.model.ShopInfoTranslation;
import com.example.travlediary.repository.info.ShopInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쇼핑 상세정보의 언어 처리.
 *
 * <p>자유 텍스트(휴점일·영업시간·주요상품·기타 안내)만 언어별로 다루고,
 * 주차 여부·연락처·홈페이지는 손대지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ShopInfoServiceLocalizedTest {

    @Mock private ShopInfoMapper shopInfoMapper;
    @InjectMocks private ShopInfoService shopInfoService;

    private ShopInfo base;

    @BeforeEach
    void setUpBase() {
        base = new ShopInfo();
        base.setDestinationId(51L);
        base.setClosedDays("매주 월요일, 명절");
        base.setOpeningHours("10:00~20:00");
        base.setMainProducts("의류, 소품, 식품");
        base.setGuide("단체 방문은 예약이 필요합니다.");
        base.setParkingAvailable(true);
        base.setContactNumber("02-1234-5678");
        base.setHomepageUrl("https://example.com");
    }

    @ParameterizedTest
    @CsvSource({
            "KOREAN, '의류, 소품, 식품'",
            "ENGLISH, 'Clothing, accessories, food'",
            "JAPANESE, '衣類・雑貨・食品'",
            "CHINESE_SIMPLIFIED, '服装、饰品、食品'",
            "CHINESE_TRADITIONAL, '服裝、飾品、食品'"
    })
    void eachSupportedLanguageReadsItsOwnText(SupportedLanguage language, String expectedProducts) {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(1L, "ko", "의류, 소품, 식품", "매주 월요일, 명절"),
                translation(2L, "en", "Clothing, accessories, food", "Mondays, holidays"),
                translation(3L, "ja", "衣類・雑貨・食品", "毎週月曜日、祝日"),
                translation(4L, "zh-CN", "服装、饰品、食品", "每周一、节假日"),
                translation(5L, "zh-TW", "服裝、飾品、食品", "每週一、節假日")));

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(51L, language);

        assertThat(localized.getMainProducts()).isEqualTo(expectedProducts);
        // 번역 조회는 여행지당 한 번이다
        verify(shopInfoMapper, times(1)).findTranslationsByDestinationId(51L);
    }

    @Test
    void fieldsFallBackIndependently() {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(1L, "ko", "의류, 소품, 식품", "매주 월요일, 명절"),
                // 영어는 주요상품만 있다
                translation(2L, "en", "Clothing, accessories, food", "   ")));

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.ENGLISH);

        assertThat(localized.getMainProducts()).isEqualTo("Clothing, accessories, food");
        assertThat(localized.getClosedDays()).isEqualTo("매주 월요일, 명절");
    }

    @Test
    void withoutRequestedAndKoreanTheFirstRemainingLanguageIsUsed() {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(3L, "zh-CN", "服装、饰品、食品", null),
                translation(2L, "en", null, "Mondays, holidays")));

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.JAPANESE);

        assertThat(localized.getMainProducts()).isEqualTo("服装、饰品、食品");
        assertThat(localized.getClosedDays()).isEqualTo("Mondays, holidays");
    }

    @Test
    void withoutAnyTranslationTheBaseValuesStayAndOtherFieldsAreUntouched() {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of());

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.ENGLISH);

        assertThat(localized.getClosedDays()).isEqualTo("매주 월요일, 명절");
        assertThat(localized.getOpeningHours()).isEqualTo("10:00~20:00");
        assertThat(localized.getMainProducts()).isEqualTo("의류, 소품, 식품");
        assertThat(localized.getGuide()).isEqualTo("단체 방문은 예약이 필요합니다.");
        // 언어와 상관없는 값은 그대로다
        assertThat(localized.getParkingAvailable()).isTrue();
        assertThat(localized.getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        // 원본 객체는 건드리지 않는다
        assertThat(base.getMainProducts()).isEqualTo("의류, 소품, 식품");
    }

    @Test
    void nonTranslatedValuesSurviveEvenWhenEveryTextIsLocalized() {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L))
                .thenReturn(List.of(fullTranslation(2L, "en")));

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.ENGLISH);

        assertThat(localized.getOpeningHours()).isEqualTo("10:00-20:00");
        assertThat(localized.getGuide()).isEqualTo("Groups should book ahead.");
        assertThat(localized.getParkingAvailable()).isTrue();
        assertThat(localized.getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        // 원본 객체는 그대로다
        assertThat(base.getOpeningHours()).isEqualTo("10:00~20:00");
    }

    @Test
    void emptyBaseAndTranslationLeaveTheFieldNull() {
        base.setGuide("  ");
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(1L, "ko", "의류", null)));

        ShopInfo localized = shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.ENGLISH);

        assertThat(localized.getGuide()).isNull();
    }

    @Test
    void translationQueryFailuresAreNotHidden() {
        givenBase();
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenThrow(schemaFailure());

        // 스키마 오류는 조용히 넘기지 않고 그대로 드러낸다
        assertThatThrownBy(() -> shopInfoService.findLocalizedByDestinationId(
                51L, SupportedLanguage.ENGLISH))
                .isInstanceOf(BadSqlGrammarException.class);
        assertThatThrownBy(() -> shopInfoService.saveTranslations(51L, base, filledSlots()))
                .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void missingShopInfoReturnsNullWithoutReadingTranslations() {
        when(shopInfoMapper.findByDestinationId(99L)).thenReturn(null);

        assertThat(shopInfoService.findLocalizedByDestinationId(
                99L, SupportedLanguage.ENGLISH)).isNull();
        verify(shopInfoMapper, never()).findTranslationsByDestinationId(99L);
    }

    @Test
    void newShopStoresKoreanFromTheBaseAndTheFourOtherLanguages() {
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of());

        shopInfoService.saveTranslations(51L, base, filledSlots());

        ArgumentCaptor<ShopInfoTranslation> captor =
                ArgumentCaptor.forClass(ShopInfoTranslation.class);
        verify(shopInfoMapper, times(5)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ShopInfoTranslation::getLanguageCode,
                        ShopInfoTranslation::getMainProducts)
                .containsExactly(
                        tuple("ko", "의류, 소품, 식품"),
                        tuple("en", "Clothing, accessories, food"),
                        tuple("ja", "衣類・雑貨・食品"),
                        tuple("zh-CN", "服装、饰品、食品"),
                        tuple("zh-TW", "服裝、飾品、食品"));
        assertThat(captor.getAllValues())
                .extracting(ShopInfoTranslation::getLanguageCode).doesNotContain("zh");
        // 한국어 줄은 원본 입력을 그대로 옮긴다
        ShopInfoTranslation korean = captor.getAllValues().get(0);
        assertThat(korean.getClosedDays()).isEqualTo("매주 월요일, 명절");
        assertThat(korean.getOpeningHours()).isEqualTo("10:00~20:00");
        assertThat(korean.getGuide()).isEqualTo("단체 방문은 예약이 필요합니다.");
    }

    @Test
    void languagesAreSavedAndClearedIndependently() {
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(1L, "ko", "의류, 소품, 식품", "매주 월요일, 명절"),
                translation(2L, "en", "Clothing, accessories, food", null),
                translation(3L, "ja", "衣類・雑貨・食品", null)));

        List<ShopInfoTranslationForm> slots = filledSlots();
        ShopInfoTranslationForm japanese = slots.get(1);
        japanese.setMainProducts("   ");

        shopInfoService.saveTranslations(51L, base, slots);

        verify(shopInfoMapper).deleteTranslation(51L, "ja");
        verify(shopInfoMapper, never()).deleteTranslation(51L, "en");
        verify(shopInfoMapper, never()).deleteTranslation(51L, "ko");
        verify(shopInfoMapper, times(2)).updateTranslation(any());  // ko, en
        verify(shopInfoMapper, times(2)).insertTranslation(any());  // zh-CN, zh-TW
    }

    @Test
    void aLanguageLeftCompletelyEmptyIsNeverInserted() {
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of());

        List<ShopInfoTranslationForm> slots = new ArrayList<>();
        slots.add(new ShopInfoTranslationForm("en"));            // 전부 비어 있다
        ShopInfoTranslationForm japanese = new ShopInfoTranslationForm("ja");
        japanese.setGuide("   ");                                 // 공백만 있어도 빈 값이다
        slots.add(japanese);
        ShopInfoTranslationForm simplified = new ShopInfoTranslationForm("zh-CN");
        simplified.setClosedDays("每周一、节假日");                  // 한 칸만 채워도 남긴다
        slots.add(simplified);

        shopInfoService.saveTranslations(51L, base, slots);

        ArgumentCaptor<ShopInfoTranslation> captor =
                ArgumentCaptor.forClass(ShopInfoTranslation.class);
        verify(shopInfoMapper, times(2)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ShopInfoTranslation::getLanguageCode)
                .containsExactly("ko", "zh-CN");
        verify(shopInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void unknownLanguageSlotsAreIgnoredAndEditFormRestoresStoredValues() {
        when(shopInfoMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of(
                translation(1L, "ko", "의류", null),
                fullTranslation(2L, "en"),
                translation(9L, "zh", "服装", null)));

        ShopInfoTranslationForm legacy = new ShopInfoTranslationForm("zh");
        legacy.setMainProducts("服装");
        shopInfoService.saveTranslations(51L, base, List.of(legacy));
        verify(shopInfoMapper, never()).insertTranslation(any());
        verify(shopInfoMapper, times(1)).updateTranslation(any());  // ko 만

        List<ShopInfoTranslationForm> slots = shopInfoService.getTranslationForms(51L);
        assertThat(slots).extracting(ShopInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(slots.get(0).getClosedDays()).isEqualTo("Mondays, holidays");
        assertThat(slots.get(0).getOpeningHours()).isEqualTo("10:00-20:00");
        assertThat(slots.get(0).getMainProducts()).isEqualTo("Clothing, accessories, food");
        assertThat(slots.get(0).getGuide()).isEqualTo("Groups should book ahead.");
        // legacy 'zh' 줄은 어느 슬롯도 채우지 않는다
        assertThat(slots.get(2).getMainProducts()).isNull();
        assertThat(slots.get(3).getMainProducts()).isNull();
    }

    private void givenBase() {
        when(shopInfoMapper.findByDestinationId(51L)).thenReturn(base);
    }

    private BadSqlGrammarException schemaFailure() {
        return new BadSqlGrammarException("SELECT", "select * from shop_info_translations",
                new SQLSyntaxErrorException("Unknown column 'main_products'"));
    }

    private List<ShopInfoTranslationForm> filledSlots() {
        List<ShopInfoTranslationForm> slots = new ArrayList<>();
        slots.add(slot("en", "Clothing, accessories, food"));
        slots.add(slot("ja", "衣類・雑貨・食品"));
        slots.add(slot("zh-CN", "服装、饰品、食品"));
        slots.add(slot("zh-TW", "服裝、飾品、食品"));
        return slots;
    }

    private ShopInfoTranslationForm slot(String languageCode, String mainProducts) {
        ShopInfoTranslationForm slot = new ShopInfoTranslationForm(languageCode);
        slot.setMainProducts(mainProducts);
        return slot;
    }

    private ShopInfoTranslation translation(Long id, String languageCode,
                                            String mainProducts, String closedDays) {
        ShopInfoTranslation translation = new ShopInfoTranslation();
        translation.setId(id);
        translation.setDestinationId(51L);
        translation.setLanguageCode(languageCode);
        translation.setMainProducts(mainProducts);
        translation.setClosedDays(closedDays);
        return translation;
    }

    /** 네 칸이 모두 찬 영어 줄. */
    private ShopInfoTranslation fullTranslation(Long id, String languageCode) {
        ShopInfoTranslation translation = translation(id, languageCode,
                "Clothing, accessories, food", "Mondays, holidays");
        translation.setOpeningHours("10:00-20:00");
        translation.setGuide("Groups should book ahead.");
        return translation;
    }
}
