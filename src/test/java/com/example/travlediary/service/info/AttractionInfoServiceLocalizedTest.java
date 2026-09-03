package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import com.example.travlediary.repository.info.AttractionInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttractionInfoServiceLocalizedTest {

    @Mock
    private AttractionInfoMapper attractionInfoMapper;

    @InjectMocks
    private AttractionInfoService attractionInfoService;

    private AttractionInfo base;

    @BeforeEach
    void setUpBase() {
        base = new AttractionInfo();
        base.setDestinationId(15L);
        base.setClosedDays("base closed days");
        base.setOpeningHours("base hours");
        base.setAdmissionFee("base fee");
        base.setGuide("base guide");
        base.setParkingAvailable(true);
        base.setContactNumber("02-3700-3900");
        base.setHomepageUrl("https://example.com");
        when(attractionInfoMapper.findByDestinationId(15L)).thenReturn(base);
    }

    @Test
    void requestedAndKoreanTranslationsFallBackIndependentlyPerField() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "화요일", "09:00~18:00", "3000원", "한국어 안내"),
                translation(2L, "en", "Tuesday", null, "KRW 3,000", null)));

        AttractionInfo localized = attractionInfoService.findLocalizedByDestinationId(
                15L, SupportedLanguage.ENGLISH);

        assertThat(localized.getClosedDays()).isEqualTo("Tuesday");
        assertThat(localized.getOpeningHours()).isEqualTo("09:00~18:00");
        assertThat(localized.getAdmissionFee()).isEqualTo("KRW 3,000");
        assertThat(localized.getGuide()).isEqualTo("한국어 안내");
        assertThat(localized.getParkingAvailable()).isTrue();
        assertThat(localized.getContactNumber()).isEqualTo("02-3700-3900");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        assertThat(base.getClosedDays()).isEqualTo("base closed days");
    }

    @ParameterizedTest
    @EnumSource(value = SupportedLanguage.class, names = {
            "JAPANESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL"
    })
    void missingRequestedTranslationUsesKoreanGuide(SupportedLanguage requestedLanguage) {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "화요일", "한국어 운영시간", "한국어 입장료", "한국어 안내")));

        AttractionInfo localized = attractionInfoService.findLocalizedByDestinationId(
                15L, requestedLanguage);

        assertThat(localized.getGuide()).isEqualTo("한국어 안내");
    }

    @Test
    void missingRequestedAndKoreanValuesUseDeterministicLanguageThenIdOrder() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(9L, "ja", "火曜日", null, null, null),
                translation(7L, "en", "later English", null, null, null),
                translation(3L, "en", "first English", null, null, null)));

        AttractionInfo localized = attractionInfoService.findLocalizedByDestinationId(
                15L, SupportedLanguage.CHINESE_TRADITIONAL);

        assertThat(localized.getClosedDays()).isEqualTo("first English");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRequestedFieldUsesKoreanField(String blank) {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "화요일", "한국어 운영시간", "한국어 입장료", "한국어 안내"),
                translation(2L, "en", blank, "English hours", "English fee", "English guide")));

        AttractionInfo localized = attractionInfoService.findLocalizedByDestinationId(
                15L, SupportedLanguage.ENGLISH);

        assertThat(localized.getClosedDays()).isEqualTo("화요일");
    }

    @Test
    void missingTranslationsUseBaseFields() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of());

        AttractionInfo localized = attractionInfoService.findLocalizedByDestinationId(
                15L, SupportedLanguage.CHINESE_SIMPLIFIED);

        assertThat(localized.getClosedDays()).isEqualTo("base closed days");
        assertThat(localized.getOpeningHours()).isEqualTo("base hours");
        assertThat(localized.getAdmissionFee()).isEqualTo("base fee");
        assertThat(localized.getGuide()).isEqualTo("base guide");
    }

    private AttractionInfoTranslation translation(Long id, String languageCode,
                                                  String closedDays, String openingHours,
                                                  String admissionFee, String guide) {
        AttractionInfoTranslation translation = new AttractionInfoTranslation();
        translation.setId(id);
        translation.setDestinationId(15L);
        translation.setLanguageCode(languageCode);
        translation.setClosedDays(closedDays);
        translation.setOpeningHours(openingHours);
        translation.setAdmissionFee(admissionFee);
        translation.setGuide(guide);
        return translation;
    }
}
