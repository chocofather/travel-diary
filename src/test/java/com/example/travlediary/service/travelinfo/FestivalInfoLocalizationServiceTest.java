package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 축제·행사 상세정보가 필드마다 따로 대체되는지 본다.
 *
 * <p>대체 순서는 요청 언어 → 한국어 → 남은 언어 → base festival_info → null 이다.
 */
@ExtendWith(MockitoExtension.class)
class FestivalInfoLocalizationServiceTest {

    @Mock private FestivalInfoMapper festivalInfoMapper;

    private FestivalInfoLocalizationService localizationService;

    @BeforeEach
    void setUp() {
        localizationService = new FestivalInfoLocalizationService(festivalInfoMapper);
    }

    @Test
    void requestedLanguageRowIsUsedWhenItHasValues() {
        FestivalInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                english(2L, 41L),
                korean(1L, 41L));

        assertThat(localized.getLanguageCode()).isEqualTo("en");
        assertThat(localized.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(localized.getAddress()).isEqualTo("161 Sajik-ro, Jongno-gu, Seoul");
        assertThat(localized.getPlayTime()).isEqualTo("Part 1 18:20-20:10");
        assertThat(localized.getUseTime()).isEqualTo("KRW 60,000 per person");
        assertThat(localized.getSponsor1()).isEqualTo("Korea Heritage Service");
        assertThat(localized.getSponsor2()).isEqualTo("Korea Heritage Agency");
    }

    @Test
    void koreanRowIsUsedWhenTheRequestedLanguageRowIsMissing() {
        FestivalInfoTranslation localized = localize(
                SupportedLanguage.JAPANESE, korean(1L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
        assertThat(localized.getUseTime()).isEqualTo("1인 60,000원");
    }

    @Test
    void deterministicRemainingLanguageIsUsedWhenKoreanIsMissingToo() {
        // language_code ASC 로 en 이 ja 보다 앞이므로 언제 불러도 en 이 나와야 한다.
        FestivalInfoTranslation japanese = new FestivalInfoTranslation();
        japanese.setId(9L);
        japanese.setInfoId(41L);
        japanese.setLanguageCode("ja");
        japanese.setEventPlace("景福宮");

        FestivalInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED, japanese, english(3L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
    }

    @Test
    void baseValuesAreUsedWhenNoTranslationRowExists() {
        FestivalInfoTranslation localized = localize(SupportedLanguage.ENGLISH);

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
        assertThat(localized.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(localized.getPlayTime()).isEqualTo("1부 18:20~20:10");
        assertThat(localized.getUseTime()).isEqualTo("1인 60,000원");
        assertThat(localized.getSponsor1()).isEqualTo("국가유산청");
        assertThat(localized.getSponsor2()).isEqualTo("국가유산진흥원");
    }

    /**
     * 행사 정보 섹션이 번역 때문에 사라지면 안 된다.
     * {@code FestivalDetailDto.isEventInfoPresent()} 가 여러 필드를 OR 로 보므로,
     * base 에 값이 있으면 대체 결과에도 반드시 남아야 한다.
     */
    @Test
    void emptyTranslationRowsNeverHideEventInfoThatTheBaseStillHas() {
        FestivalInfoTranslation blankEnglish = new FestivalInfoTranslation();
        blankEnglish.setId(2L);
        blankEnglish.setInfoId(41L);
        blankEnglish.setLanguageCode("en");
        blankEnglish.setEventPlace("   ");
        blankEnglish.setAddress("");

        FestivalInfoTranslation localized = localize(SupportedLanguage.ENGLISH, blankEnglish);

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
        assertThat(localized.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(localized.getPlayTime()).isEqualTo("1부 18:20~20:10");
        assertThat(localized.getUseTime()).isEqualTo("1인 60,000원");
        assertThat(localized.getSponsor1()).isEqualTo("국가유산청");
        assertThat(localized.getSponsor2()).isEqualTo("국가유산진흥원");
    }

    @Test
    void missingValueEverywhereFallsBackToNull() {
        FestivalInfo emptyBase = new FestivalInfo();
        emptyBase.setInfoId(41L);

        FestivalInfoTranslation localized = localizationService.resolveLocalizedInfo(
                emptyBase, List.of(), SupportedLanguage.ENGLISH);

        assertThat(localized.getEventPlace()).isNull();
        assertThat(localized.getAddress()).isNull();
        assertThat(localized.getPlayTime()).isNull();
        assertThat(localized.getUseTime()).isNull();
        assertThat(localized.getSponsor1()).isNull();
        assertThat(localized.getSponsor2()).isNull();
    }

    @Test
    void everyFieldFallsBackIndependently() {
        // en 줄에 장소·주최만 있고 나머지는 비어 있다.
        FestivalInfoTranslation partialEnglish = new FestivalInfoTranslation();
        partialEnglish.setId(2L);
        partialEnglish.setInfoId(41L);
        partialEnglish.setLanguageCode("en");
        partialEnglish.setEventPlace("Gyeongbokgung Palace");
        partialEnglish.setSponsor1("Korea Heritage Service");

        FestivalInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH, partialEnglish, korean(1L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(localized.getSponsor1()).isEqualTo("Korea Heritage Service");
        // 나머지는 ko 로 떨어진다
        assertThat(localized.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(localized.getPlayTime()).isEqualTo("1부 18:20~20:10");
        assertThat(localized.getUseTime()).isEqualTo("1인 60,000원");
        assertThat(localized.getSponsor2()).isEqualTo("국가유산진흥원");
    }

    @Test
    void blankTranslationValueIsTreatedAsMissing() {
        FestivalInfoTranslation blankUseTime = english(2L, 41L);
        blankUseTime.setUseTime("   ");

        FestivalInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH, blankUseTime, korean(1L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(localized.getUseTime()).isEqualTo("1인 60,000원");
    }

    @Test
    void traditionalChineseDoesNotPreferSimplifiedChineseOverKorean() {
        FestivalInfoTranslation simplified = new FestivalInfoTranslation();
        simplified.setId(4L);
        simplified.setInfoId(41L);
        simplified.setLanguageCode("zh-CN");
        simplified.setEventPlace("景福宫");

        FestivalInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_TRADITIONAL, simplified, korean(1L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void simplifiedChineseDoesNotPreferTraditionalChineseOverKorean() {
        FestivalInfoTranslation traditional = new FestivalInfoTranslation();
        traditional.setId(5L);
        traditional.setInfoId(41L);
        traditional.setLanguageCode("zh-TW");
        traditional.setEventPlace("景福宮");

        FestivalInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED, traditional, korean(1L, 41L));

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void singleLookupReadsTranslationsOnceForTheRequestedFestival() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L))
                .thenReturn(List.of(english(2L, 41L)));

        FestivalInfoTranslation localized = localizationService.resolveLocalizedInfo(
                base(41L), SupportedLanguage.ENGLISH);

        assertThat(localized.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        verify(festivalInfoMapper, times(1)).findTranslationsByInfoId(41L);
        verify(festivalInfoMapper, never()).findTranslationsByInfoIds(anyList());
    }

    @Test
    void listLocalizationReadsEveryTranslationInASingleQuery() {
        when(festivalInfoMapper.findTranslationsByInfoIds(List.of(41L, 42L, 43L)))
                .thenReturn(List.of(
                        korean(1L, 41L),
                        english(2L, 41L),
                        korean(3L, 42L)));

        Map<Long, FestivalInfoTranslation> localized =
                localizationService.resolveLocalizedInfoByInfoIds(
                        List.of(base(41L), base(42L), base(43L)), SupportedLanguage.ENGLISH);

        assertThat(localized.get(41L).getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(localized.get(42L).getEventPlace()).isEqualTo("경복궁");
        // 번역이 하나도 없는 축제는 base 로 내려온다.
        assertThat(localized.get(43L).getEventPlace()).isEqualTo("경복궁");

        verify(festivalInfoMapper, times(1)).findTranslationsByInfoIds(List.of(41L, 42L, 43L));
        verify(festivalInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    @Test
    void emptyListNeverReadsTranslations() {
        assertThat(localizationService.resolveLocalizedInfoByInfoIds(
                List.of(), SupportedLanguage.ENGLISH)).isEmpty();
        assertThat(localizationService.resolveLocalizedInfoByInfoIds(
                null, SupportedLanguage.ENGLISH)).isEmpty();

        verifyNoInteractions(festivalInfoMapper);
    }

    @Test
    void localizationNeverMutatesTheBaseOrTheTranslationRowsItWasGiven() {
        FestivalInfo base = base(41L);
        FestivalInfoTranslation koreanRow = korean(1L, 41L);

        FestivalInfoTranslation localized = localizationService.resolveLocalizedInfo(
                base, List.of(koreanRow), SupportedLanguage.ENGLISH);

        assertThat(localized).isNotSameAs(koreanRow);
        assertThat(base.getEventPlace()).isEqualTo("경복궁");
        assertThat(base.getContactTel()).isEqualTo("1522-2295");
        assertThat(koreanRow.getEventPlace()).isEqualTo("경복궁");
        assertThat(koreanRow.getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void rowsBelongingToAnotherFestivalAreIgnored() {
        FestivalInfoTranslation otherFestival = english(2L, 99L);

        FestivalInfoTranslation localized = localizationService.resolveLocalizedInfo(
                base(41L), List.of(otherFestival), SupportedLanguage.ENGLISH);

        assertThat(localized.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void missingBaseYieldsNothingToLocalize() {
        assertThat(localizationService.resolveLocalizedInfo(null, SupportedLanguage.ENGLISH))
                .isNull();
        assertThat(localizationService.resolveLocalizedInfo(
                new FestivalInfo(), SupportedLanguage.ENGLISH)).isNull();

        verifyNoInteractions(festivalInfoMapper);
    }

    private FestivalInfoTranslation localize(SupportedLanguage language,
                                             FestivalInfoTranslation... translations) {
        return localizationService.resolveLocalizedInfo(
                base(41L), List.of(translations), language);
    }

    private FestivalInfo base(Long infoId) {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(infoId);
        info.setEventPlace("경복궁");
        info.setAddress("서울특별시 종로구 사직로 161");
        info.setPlayTime("1부 18:20~20:10");
        info.setUseTime("1인 60,000원");
        info.setSponsor1("국가유산청");
        info.setSponsor2("국가유산진흥원");
        info.setSponsor1Tel("02-1234-5678");
        info.setSponsor2Tel("02-9876-5432");
        info.setContactTel("1522-2295");
        info.setHomepageUrl("https://www.example.com/festival");
        info.setSourceType("KTO_TOURAPI");
        info.setExternalContentId("2648460");
        return info;
    }

    private FestivalInfoTranslation korean(Long id, Long infoId) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(infoId);
        translation.setLanguageCode("ko");
        translation.setEventPlace("경복궁");
        translation.setAddress("서울특별시 종로구 사직로 161");
        translation.setPlayTime("1부 18:20~20:10");
        translation.setUseTime("1인 60,000원");
        translation.setSponsor1("국가유산청");
        translation.setSponsor2("국가유산진흥원");
        return translation;
    }

    private FestivalInfoTranslation english(Long id, Long infoId) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(infoId);
        translation.setLanguageCode("en");
        translation.setEventPlace("Gyeongbokgung Palace");
        translation.setAddress("161 Sajik-ro, Jongno-gu, Seoul");
        translation.setPlayTime("Part 1 18:20-20:10");
        translation.setUseTime("KRW 60,000 per person");
        translation.setSponsor1("Korea Heritage Service");
        translation.setSponsor2("Korea Heritage Agency");
        return translation;
    }
}
