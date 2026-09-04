package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AccommodationInfoTranslationForm;
import com.example.travlediary.model.AccommodationInfo;
import com.example.travlediary.model.AccommodationInfoTranslation;
import com.example.travlediary.repository.info.AccommodationInfoMapper;
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
 * 숙박 상세정보의 언어 처리.
 *
 * <p>자유 텍스트(객실 유형·기타 안내)만 언어별로 다루고, 나머지 값은 손대지 않는다.
 * 번역 테이블을 아직 만들지 않은 환경에서도 원본만으로 동작해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationInfoServiceLocalizedTest {

    @Mock private AccommodationInfoMapper accommodationInfoMapper;
    @InjectMocks private AccommodationInfoService accommodationInfoService;

    private AccommodationInfo base;

    @BeforeEach
    void setUpBase() {
        base = new AccommodationInfo();
        base.setDestinationId(31L);
        base.setRoomType("싱글, 트윈, 더블");
        base.setEtc("전 객실 금연");
        base.setCheckinTime("15:00");
        base.setCheckoutTime("11:00");
        base.setRoomCount(50);
        base.setStarRating(4.5);
        base.setBreakfastIncluded(true);
        base.setParkingAvailable(true);
        base.setPetAllowed(false);
        base.setContactNumber("042-000-1234");
        base.setHomepageUrl("https://example.com");
    }

    @ParameterizedTest
    @CsvSource({
            "KOREAN, '싱글, 트윈, 더블'",
            "ENGLISH, 'Single, Twin, Double'",
            "JAPANESE, 'シングル・ツイン・ダブル'",
            "CHINESE_SIMPLIFIED, '单人房、双床房、双人房'",
            "CHINESE_TRADITIONAL, '單人房、雙床房、雙人房'"
    })
    void eachSupportedLanguageReadsItsOwnText(SupportedLanguage language, String expectedRoomType) {
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(1L, "ko", "싱글, 트윈, 더블", "전 객실 금연"),
                translation(2L, "en", "Single, Twin, Double", "All rooms non-smoking"),
                translation(3L, "ja", "シングル・ツイン・ダブル", "全室禁煙"),
                translation(4L, "zh-CN", "单人房、双床房、双人房", "全部客房禁烟"),
                translation(5L, "zh-TW", "單人房、雙床房、雙人房", "全部客房禁菸")));

        AccommodationInfo localized =
                accommodationInfoService.findLocalizedByDestinationId(31L, language);

        assertThat(localized.getRoomType()).isEqualTo(expectedRoomType);
        // 번역 조회는 여행지당 한 번이다
        verify(accommodationInfoMapper, times(1)).findTranslationsByDestinationId(31L);
    }

    @Test
    void fieldsFallBackIndependently() {
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(1L, "ko", "싱글, 트윈, 더블", "전 객실 금연"),
                // 영어는 객실 유형만 있다
                translation(2L, "en", "Single, Twin, Double", "   ")));

        AccommodationInfo localized = accommodationInfoService.findLocalizedByDestinationId(
                31L, SupportedLanguage.ENGLISH);

        assertThat(localized.getRoomType()).isEqualTo("Single, Twin, Double");
        assertThat(localized.getEtc()).isEqualTo("전 객실 금연");
    }

    @Test
    void withoutRequestedAndKoreanTheFirstRemainingLanguageIsUsed() {
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(3L, "zh-CN", "单人房", null),
                translation(2L, "en", null, "All rooms non-smoking")));

        AccommodationInfo localized = accommodationInfoService.findLocalizedByDestinationId(
                31L, SupportedLanguage.JAPANESE);

        assertThat(localized.getEtc()).isEqualTo("All rooms non-smoking");
        assertThat(localized.getRoomType()).isEqualTo("单人房");
    }

    @Test
    void withoutAnyTranslationTheBaseValuesStayAndOtherFieldsAreUntouched() {
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of());

        AccommodationInfo localized = accommodationInfoService.findLocalizedByDestinationId(
                31L, SupportedLanguage.ENGLISH);

        assertThat(localized.getRoomType()).isEqualTo("싱글, 트윈, 더블");
        assertThat(localized.getEtc()).isEqualTo("전 객실 금연");
        // 언어와 상관없는 값은 그대로다
        assertThat(localized.getCheckinTime()).isEqualTo("15:00");
        assertThat(localized.getCheckoutTime()).isEqualTo("11:00");
        assertThat(localized.getRoomCount()).isEqualTo(50);
        assertThat(localized.getStarRating()).isEqualTo(4.5);
        assertThat(localized.getBreakfastIncluded()).isTrue();
        assertThat(localized.getPetAllowed()).isFalse();
        assertThat(localized.getContactNumber()).isEqualTo("042-000-1234");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        // 원본 객체는 건드리지 않는다
        assertThat(base.getRoomType()).isEqualTo("싱글, 트윈, 더블");
    }

    @Test
    void emptyBaseAndTranslationLeaveTheFieldNull() {
        base.setEtc("  ");
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(1L, "ko", "싱글", null)));

        AccommodationInfo localized = accommodationInfoService.findLocalizedByDestinationId(
                31L, SupportedLanguage.ENGLISH);

        assertThat(localized.getEtc()).isNull();
    }

    @Test
    void translationQueryFailuresAreNotHidden() {
        givenBase();
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L))
                .thenThrow(schemaFailure());

        // 스키마 오류는 조용히 넘기지 않고 그대로 드러낸다
        assertThatThrownBy(() -> accommodationInfoService.findLocalizedByDestinationId(
                31L, SupportedLanguage.ENGLISH))
                .isInstanceOf(BadSqlGrammarException.class);
        assertThatThrownBy(() -> accommodationInfoService.saveTranslations(
                31L, base, filledSlots()))
                .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void missingAccommodationInfoReturnsNullWithoutReadingTranslations() {
        when(accommodationInfoMapper.findByDestinationId(99L)).thenReturn(null);

        assertThat(accommodationInfoService.findLocalizedByDestinationId(
                99L, SupportedLanguage.ENGLISH)).isNull();
        verify(accommodationInfoMapper, never()).findTranslationsByDestinationId(99L);
    }

    @Test
    void newAccommodationStoresKoreanFromTheBaseAndTheFourOtherLanguages() {
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of());

        accommodationInfoService.saveTranslations(31L, base, filledSlots());

        ArgumentCaptor<AccommodationInfoTranslation> captor =
                ArgumentCaptor.forClass(AccommodationInfoTranslation.class);
        verify(accommodationInfoMapper, times(5)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccommodationInfoTranslation::getLanguageCode,
                        AccommodationInfoTranslation::getRoomType)
                .containsExactly(
                        tuple("ko", "싱글, 트윈, 더블"),
                        tuple("en", "Single, Twin, Double"),
                        tuple("ja", "シングル"),
                        tuple("zh-CN", "单人房"),
                        tuple("zh-TW", "單人房"));
        assertThat(captor.getAllValues())
                .extracting(AccommodationInfoTranslation::getLanguageCode).doesNotContain("zh");
        assertThat(captor.getAllValues().get(0).getEtc()).isEqualTo("전 객실 금연");
    }

    @Test
    void languagesAreSavedAndClearedIndependently() {
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(1L, "ko", "싱글, 트윈, 더블", "전 객실 금연"),
                translation(2L, "en", "Single, Twin, Double", null),
                translation(3L, "ja", "シングル", null)));

        List<AccommodationInfoTranslationForm> slots = filledSlots();
        AccommodationInfoTranslationForm japanese = slots.get(1);
        japanese.setRoomType("   ");
        japanese.setEtc(null);

        accommodationInfoService.saveTranslations(31L, base, slots);

        verify(accommodationInfoMapper).deleteTranslation(31L, "ja");
        verify(accommodationInfoMapper, never()).deleteTranslation(31L, "en");
        verify(accommodationInfoMapper, never()).deleteTranslation(31L, "ko");
        verify(accommodationInfoMapper, times(2)).updateTranslation(any());  // ko, en
        verify(accommodationInfoMapper, times(2)).insertTranslation(any());  // zh-CN, zh-TW
    }

    @Test
    void unknownLanguageSlotsAreIgnoredAndEditFormRestoresStoredValues() {
        when(accommodationInfoMapper.findTranslationsByDestinationId(31L)).thenReturn(List.of(
                translation(1L, "ko", "싱글", null),
                translation(2L, "en", "Single, Twin, Double", "All rooms non-smoking"),
                translation(9L, "zh", "单人房", null)));

        AccommodationInfoTranslationForm legacy = new AccommodationInfoTranslationForm("zh");
        legacy.setRoomType("单人房");
        accommodationInfoService.saveTranslations(31L, base, List.of(legacy));
        verify(accommodationInfoMapper, never()).insertTranslation(any());
        verify(accommodationInfoMapper, times(1)).updateTranslation(any());  // ko 만

        List<AccommodationInfoTranslationForm> slots =
                accommodationInfoService.getTranslationForms(31L);
        assertThat(slots).extracting(AccommodationInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(slots.get(0).getRoomType()).isEqualTo("Single, Twin, Double");
        assertThat(slots.get(0).getEtc()).isEqualTo("All rooms non-smoking");
        // legacy 'zh' 줄은 어느 슬롯도 채우지 않는다
        assertThat(slots.get(2).getRoomType()).isNull();
        assertThat(slots.get(3).getRoomType()).isNull();
    }

    private void givenBase() {
        when(accommodationInfoMapper.findByDestinationId(31L)).thenReturn(base);
    }

    private BadSqlGrammarException schemaFailure() {
        return new BadSqlGrammarException("SELECT", "select * from accommodation_info_translations",
                new SQLSyntaxErrorException("Unknown column 'room_type'"));
    }

    private List<AccommodationInfoTranslationForm> filledSlots() {
        List<AccommodationInfoTranslationForm> slots = new ArrayList<>();
        slots.add(slot("en", "Single, Twin, Double"));
        slots.add(slot("ja", "シングル"));
        slots.add(slot("zh-CN", "单人房"));
        slots.add(slot("zh-TW", "單人房"));
        return slots;
    }

    private AccommodationInfoTranslationForm slot(String languageCode, String roomType) {
        AccommodationInfoTranslationForm slot = new AccommodationInfoTranslationForm(languageCode);
        slot.setRoomType(roomType);
        return slot;
    }

    private AccommodationInfoTranslation translation(Long id, String languageCode,
                                                     String roomType, String etc) {
        AccommodationInfoTranslation translation = new AccommodationInfoTranslation();
        translation.setId(id);
        translation.setDestinationId(31L);
        translation.setLanguageCode(languageCode);
        translation.setRoomType(roomType);
        translation.setEtc(etc);
        return translation;
    }
}
