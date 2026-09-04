package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.ActivityInfoTranslationForm;
import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.ActivityInfoTranslation;
import com.example.travlediary.repository.info.ActivityInfoMapper;
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
 * 체험/액티비티 상세정보의 언어 처리.
 *
 * <p>자유 텍스트(운영 시간·소요 시간·참가비·연령 제한·이용 안내)만 언어별로 다루고,
 * 여부 값·연락처·홈페이지는 손대지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityInfoServiceLocalizedTest {

    @Mock private ActivityInfoMapper activityInfoMapper;
    @InjectMocks private ActivityInfoService activityInfoService;

    private ActivityInfo base;

    @BeforeEach
    void setUpBase() {
        base = new ActivityInfo();
        base.setDestinationId(41L);
        base.setOpeningHours("09:00~18:00");
        base.setRequiredTime("약 2시간");
        base.setAdmissionFee("20,000원");
        base.setAgeLimit("7세 이상");
        base.setGuide("우천 시 운영하지 않습니다.");
        base.setReservation(true);
        base.setEquipmentIncluded(true);
        base.setParkingAvailable(false);
        base.setContactNumber("02-1234-5678");
        base.setHomepageUrl("https://example.com");
    }

    @ParameterizedTest
    @CsvSource({
            "KOREAN, 약 2시간",
            "ENGLISH, About 2 hours",
            "JAPANESE, 約2時間",
            "CHINESE_SIMPLIFIED, 约2小时",
            "CHINESE_TRADITIONAL, 約2小時"
    })
    void eachSupportedLanguageReadsItsOwnText(SupportedLanguage language, String expectedTime) {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(1L, "ko", "약 2시간", "7세 이상"),
                translation(2L, "en", "About 2 hours", "Ages 7 and up"),
                translation(3L, "ja", "約2時間", "7歳以上"),
                translation(4L, "zh-CN", "约2小时", "7岁以上"),
                translation(5L, "zh-TW", "約2小時", "7歲以上")));

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(41L, language);

        assertThat(localized.getRequiredTime()).isEqualTo(expectedTime);
        // 번역 조회는 여행지당 한 번이다
        verify(activityInfoMapper, times(1)).findTranslationsByDestinationId(41L);
    }

    @Test
    void fieldsFallBackIndependently() {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(1L, "ko", "약 2시간", "7세 이상"),
                // 영어는 소요 시간만 있다
                translation(2L, "en", "About 2 hours", "   ")));

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.ENGLISH);

        assertThat(localized.getRequiredTime()).isEqualTo("About 2 hours");
        assertThat(localized.getAgeLimit()).isEqualTo("7세 이상");
    }

    @Test
    void withoutRequestedAndKoreanTheFirstRemainingLanguageIsUsed() {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(3L, "zh-CN", "约2小时", null),
                translation(2L, "en", null, "Ages 7 and up")));

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.JAPANESE);

        assertThat(localized.getRequiredTime()).isEqualTo("约2小时");
        assertThat(localized.getAgeLimit()).isEqualTo("Ages 7 and up");
    }

    @Test
    void withoutAnyTranslationTheBaseValuesStayAndOtherFieldsAreUntouched() {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of());

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.ENGLISH);

        assertThat(localized.getOpeningHours()).isEqualTo("09:00~18:00");
        assertThat(localized.getRequiredTime()).isEqualTo("약 2시간");
        assertThat(localized.getAdmissionFee()).isEqualTo("20,000원");
        assertThat(localized.getAgeLimit()).isEqualTo("7세 이상");
        assertThat(localized.getGuide()).isEqualTo("우천 시 운영하지 않습니다.");
        // 언어와 상관없는 값은 그대로다
        assertThat(localized.getReservation()).isTrue();
        assertThat(localized.getEquipmentIncluded()).isTrue();
        assertThat(localized.getParkingAvailable()).isFalse();
        assertThat(localized.getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
        // 원본 객체는 건드리지 않는다
        assertThat(base.getRequiredTime()).isEqualTo("약 2시간");
    }

    @Test
    void nonTranslatedValuesSurviveEvenWhenEveryTextIsLocalized() {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                fullTranslation(2L, "en")));

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.ENGLISH);

        assertThat(localized.getOpeningHours()).isEqualTo("09:00-18:00");
        assertThat(localized.getGuide()).isEqualTo("Closed on rainy days.");
        assertThat(localized.getReservation()).isTrue();
        assertThat(localized.getEquipmentIncluded()).isTrue();
        assertThat(localized.getParkingAvailable()).isFalse();
        assertThat(localized.getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(localized.getHomepageUrl()).isEqualTo("https://example.com");
    }

    @Test
    void emptyBaseAndTranslationLeaveTheFieldNull() {
        base.setGuide("  ");
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(1L, "ko", "약 2시간", null)));

        ActivityInfo localized = activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.ENGLISH);

        assertThat(localized.getGuide()).isNull();
    }

    @Test
    void translationQueryFailuresAreNotHidden() {
        givenBase();
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenThrow(schemaFailure());

        // 스키마 오류는 조용히 넘기지 않고 그대로 드러낸다
        assertThatThrownBy(() -> activityInfoService.findLocalizedByDestinationId(
                41L, SupportedLanguage.ENGLISH))
                .isInstanceOf(BadSqlGrammarException.class);
        assertThatThrownBy(() -> activityInfoService.saveTranslations(41L, base, filledSlots()))
                .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void missingActivityInfoReturnsNullWithoutReadingTranslations() {
        when(activityInfoMapper.findByDestinationId(99L)).thenReturn(null);

        assertThat(activityInfoService.findLocalizedByDestinationId(
                99L, SupportedLanguage.ENGLISH)).isNull();
        verify(activityInfoMapper, never()).findTranslationsByDestinationId(99L);
    }

    @Test
    void newActivityStoresKoreanFromTheBaseAndTheFourOtherLanguages() {
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of());

        activityInfoService.saveTranslations(41L, base, filledSlots());

        ArgumentCaptor<ActivityInfoTranslation> captor =
                ArgumentCaptor.forClass(ActivityInfoTranslation.class);
        verify(activityInfoMapper, times(5)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ActivityInfoTranslation::getLanguageCode,
                        ActivityInfoTranslation::getRequiredTime)
                .containsExactly(
                        tuple("ko", "약 2시간"),
                        tuple("en", "About 2 hours"),
                        tuple("ja", "約2時間"),
                        tuple("zh-CN", "约2小时"),
                        tuple("zh-TW", "約2小時"));
        assertThat(captor.getAllValues())
                .extracting(ActivityInfoTranslation::getLanguageCode).doesNotContain("zh");
        // 한국어 줄은 원본 입력을 그대로 옮긴다
        ActivityInfoTranslation korean = captor.getAllValues().get(0);
        assertThat(korean.getOpeningHours()).isEqualTo("09:00~18:00");
        assertThat(korean.getAdmissionFee()).isEqualTo("20,000원");
        assertThat(korean.getAgeLimit()).isEqualTo("7세 이상");
        assertThat(korean.getGuide()).isEqualTo("우천 시 운영하지 않습니다.");
    }

    @Test
    void languagesAreSavedAndClearedIndependently() {
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(1L, "ko", "약 2시간", "7세 이상"),
                translation(2L, "en", "About 2 hours", null),
                translation(3L, "ja", "約2時間", null)));

        List<ActivityInfoTranslationForm> slots = filledSlots();
        ActivityInfoTranslationForm japanese = slots.get(1);
        japanese.setRequiredTime("   ");

        activityInfoService.saveTranslations(41L, base, slots);

        verify(activityInfoMapper).deleteTranslation(41L, "ja");
        verify(activityInfoMapper, never()).deleteTranslation(41L, "en");
        verify(activityInfoMapper, never()).deleteTranslation(41L, "ko");
        verify(activityInfoMapper, times(2)).updateTranslation(any());  // ko, en
        verify(activityInfoMapper, times(2)).insertTranslation(any());  // zh-CN, zh-TW
    }

    @Test
    void aLanguageLeftCompletelyEmptyIsNeverInserted() {
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of());

        List<ActivityInfoTranslationForm> slots = new ArrayList<>();
        slots.add(new ActivityInfoTranslationForm("en"));           // 전부 비어 있다
        ActivityInfoTranslationForm japanese = new ActivityInfoTranslationForm("ja");
        japanese.setGuide("   ");                                    // 공백만 있어도 빈 값이다
        slots.add(japanese);
        ActivityInfoTranslationForm simplified = new ActivityInfoTranslationForm("zh-CN");
        simplified.setAgeLimit("7岁以上");                            // 한 칸만 채워도 남긴다
        slots.add(simplified);

        activityInfoService.saveTranslations(41L, base, slots);

        ArgumentCaptor<ActivityInfoTranslation> captor =
                ArgumentCaptor.forClass(ActivityInfoTranslation.class);
        verify(activityInfoMapper, times(2)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ActivityInfoTranslation::getLanguageCode)
                .containsExactly("ko", "zh-CN");
        verify(activityInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void unknownLanguageSlotsAreIgnoredAndEditFormRestoresStoredValues() {
        when(activityInfoMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of(
                translation(1L, "ko", "약 2시간", null),
                fullTranslation(2L, "en"),
                translation(9L, "zh", "约2小时", null)));

        ActivityInfoTranslationForm legacy = new ActivityInfoTranslationForm("zh");
        legacy.setRequiredTime("约2小时");
        activityInfoService.saveTranslations(41L, base, List.of(legacy));
        verify(activityInfoMapper, never()).insertTranslation(any());
        verify(activityInfoMapper, times(1)).updateTranslation(any());  // ko 만

        List<ActivityInfoTranslationForm> slots = activityInfoService.getTranslationForms(41L);
        assertThat(slots).extracting(ActivityInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(slots.get(0).getOpeningHours()).isEqualTo("09:00-18:00");
        assertThat(slots.get(0).getRequiredTime()).isEqualTo("About 2 hours");
        assertThat(slots.get(0).getAdmissionFee()).isEqualTo("KRW 20,000");
        assertThat(slots.get(0).getAgeLimit()).isEqualTo("Ages 7 and up");
        assertThat(slots.get(0).getGuide()).isEqualTo("Closed on rainy days.");
        // legacy 'zh' 줄은 어느 슬롯도 채우지 않는다
        assertThat(slots.get(2).getRequiredTime()).isNull();
        assertThat(slots.get(3).getRequiredTime()).isNull();
    }

    private void givenBase() {
        when(activityInfoMapper.findByDestinationId(41L)).thenReturn(base);
    }

    private BadSqlGrammarException schemaFailure() {
        return new BadSqlGrammarException("SELECT", "select * from activity_info_translations",
                new SQLSyntaxErrorException("Unknown column 'required_time'"));
    }

    private List<ActivityInfoTranslationForm> filledSlots() {
        List<ActivityInfoTranslationForm> slots = new ArrayList<>();
        slots.add(slot("en", "About 2 hours"));
        slots.add(slot("ja", "約2時間"));
        slots.add(slot("zh-CN", "约2小时"));
        slots.add(slot("zh-TW", "約2小時"));
        return slots;
    }

    private ActivityInfoTranslationForm slot(String languageCode, String requiredTime) {
        ActivityInfoTranslationForm slot = new ActivityInfoTranslationForm(languageCode);
        slot.setRequiredTime(requiredTime);
        return slot;
    }

    private ActivityInfoTranslation translation(Long id, String languageCode,
                                                String requiredTime, String ageLimit) {
        ActivityInfoTranslation translation = new ActivityInfoTranslation();
        translation.setId(id);
        translation.setDestinationId(41L);
        translation.setLanguageCode(languageCode);
        translation.setRequiredTime(requiredTime);
        translation.setAgeLimit(ageLimit);
        return translation;
    }

    /** 다섯 칸이 모두 찬 영어 줄. */
    private ActivityInfoTranslation fullTranslation(Long id, String languageCode) {
        ActivityInfoTranslation translation = translation(id, languageCode,
                "About 2 hours", "Ages 7 and up");
        translation.setOpeningHours("09:00-18:00");
        translation.setAdmissionFee("KRW 20,000");
        translation.setGuide("Closed on rainy days.");
        return translation;
    }
}
