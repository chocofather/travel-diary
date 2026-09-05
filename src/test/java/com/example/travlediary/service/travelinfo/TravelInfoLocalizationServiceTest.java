package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
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
 * 여행정보 제목·본문이 필드마다 따로 대체되는지 본다.
 *
 * <p>대체 순서는 요청 언어 → 한국어 → 남은 언어 → base → null 이다.
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoLocalizationServiceTest {

    private static final String BASE_TITLE = "여행정보 원문 제목";
    private static final String BASE_CONTENT = "<p>여행정보 원문 본문</p>";

    @Mock private TravelInfoMapper travelInfoMapper;

    private TravelInfoLocalizationService localizationService;

    @BeforeEach
    void setUp() {
        localizationService = new TravelInfoLocalizationService(travelInfoMapper);
    }

    @Test
    void requestedLanguageRowIsUsedWhenItHasValues() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title", "<p>English body</p>"),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getLanguageCode()).isEqualTo("en");
        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void koreanRowIsUsedWhenRequestedLanguageRowIsMissing() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.JAPANESE,
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void deterministicRemainingLanguageIsUsedWhenKoreanIsMissingToo() {
        // language_code ASC 로 en 이 ja 보다 앞이므로 언제 불러도 en 이 나와야 한다.
        TravelInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED,
                translation(9L, 7L, "ja", "日本語タイトル", "<p>日本語本文</p>"),
                translation(3L, 7L, "en", "English title", "<p>English body</p>"));

        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void baseValuesAreUsedWhenNoTranslationRowExists() {
        TravelInfoTranslation localized = localize(SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(localized.getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void missingValueEverywhereFallsBackToNull() {
        TravelInfoTranslation localized = localizationService.resolveLocalizedContent(
                7L, null, null, List.of(), SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isNull();
        assertThat(localized.getContent()).isNull();
    }

    @Test
    void titleAndContentFallBackIndependently() {
        // en 줄에 제목만 있으면 제목은 en, 본문은 ko 가 되어야 한다.
        TravelInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title", "   "),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getTitle()).isEqualTo("English title");
        assertThat(localized.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void traditionalChineseDoesNotPreferSimplifiedChineseOverKorean() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_TRADITIONAL,
                translation(4L, 7L, "zh-CN", "简体标题", "<p>简体正文</p>"),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void simplifiedChineseDoesNotPreferTraditionalChineseOverKorean() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.CHINESE_SIMPLIFIED,
                translation(5L, 7L, "zh-TW", "繁體標題", "<p>繁體正文</p>"),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getTitle()).isEqualTo("한국어 제목");
        assertThat(localized.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void emptyQuillHtmlIsTreatedAsMissingContent() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title", "<p><br></p>"),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void contentWithOnlyAnImageStaysAValidTranslation() {
        TravelInfoTranslation localized = localize(
                SupportedLanguage.ENGLISH,
                translation(2L, 7L, "en", "English title",
                        "<p><img src=\"/uploads/editor/en-infographic.png\"></p>"),
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>"));

        assertThat(localized.getContent())
                .isEqualTo("<p><img src=\"/uploads/editor/en-infographic.png\"></p>");
    }

    @Test
    void singleLookupReadsTranslationsOnceForTheRequestedTravelInfo() {
        when(travelInfoMapper.findTranslationsByInfoId(7L)).thenReturn(List.of(
                translation(2L, 7L, "en", "English title", "<p>English body</p>")));

        TravelInfoTranslation localized = localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_CONTENT, SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo("English title");
        verify(travelInfoMapper, times(1)).findTranslationsByInfoId(7L);
        verify(travelInfoMapper, never()).findTranslationsByInfoIds(anyList());
    }

    @Test
    void listLocalizationReadsEveryTranslationInASingleQuery() {
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(7L, 8L, 9L))).thenReturn(List.of(
                translation(1L, 7L, "ko", "칠번 한국어", "<p>칠번 한국어 본문</p>"),
                translation(2L, 7L, "en", "Seven English", "<p>Seven English body</p>"),
                translation(3L, 8L, "ko", "팔번 한국어", "<p>팔번 한국어 본문</p>")));

        Map<Long, String> baseTitles = new LinkedHashMap<>();
        baseTitles.put(7L, "칠번 원문");
        baseTitles.put(8L, "팔번 원문");
        baseTitles.put(9L, "구번 원문");

        Map<Long, TravelInfoTranslation> localized =
                localizationService.resolveLocalizedContentByInfoIds(
                        baseTitles,
                        Map.of(9L, "<p>구번 원문 본문</p>"),
                        SupportedLanguage.ENGLISH);

        assertThat(localized.get(7L).getTitle()).isEqualTo("Seven English");
        assertThat(localized.get(8L).getTitle()).isEqualTo("팔번 한국어");
        // 번역이 하나도 없는 여행정보는 base 로 내려온다.
        assertThat(localized.get(9L).getTitle()).isEqualTo("구번 원문");
        assertThat(localized.get(9L).getContent()).isEqualTo("<p>구번 원문 본문</p>");

        verify(travelInfoMapper, times(1)).findTranslationsByInfoIds(List.of(7L, 8L, 9L));
        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    @Test
    void emptyListNeverReadsTranslations() {
        Map<Long, TravelInfoTranslation> localized =
                localizationService.resolveLocalizedContentByInfoIds(
                        Map.of(), Map.of(), SupportedLanguage.ENGLISH);

        assertThat(localized).isEmpty();
        verifyNoInteractions(travelInfoMapper);
    }

    @Test
    void localizationNeverMutatesTheTranslationRowsItWasGiven() {
        TravelInfoTranslation koreanRow =
                translation(1L, 7L, "ko", "한국어 제목", "<p>한국어 본문</p>");

        TravelInfoTranslation localized = localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_CONTENT, List.of(koreanRow), SupportedLanguage.ENGLISH);

        assertThat(localized).isNotSameAs(koreanRow);
        assertThat(koreanRow.getTitle()).isEqualTo("한국어 제목");
        assertThat(koreanRow.getContent()).isEqualTo("<p>한국어 본문</p>");
        assertThat(koreanRow.getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void rowsBelongingToAnotherTravelInfoAreIgnored() {
        TravelInfoTranslation localized = localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_CONTENT,
                List.of(translation(2L, 99L, "en", "Other travel info", "<p>Other body</p>")),
                SupportedLanguage.ENGLISH);

        assertThat(localized.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(localized.getContent()).isEqualTo(BASE_CONTENT);
    }

    private TravelInfoTranslation localize(SupportedLanguage language,
                                           TravelInfoTranslation... translations) {
        return localizationService.resolveLocalizedContent(
                7L, BASE_TITLE, BASE_CONTENT, List.of(translations), language);
    }

    private TravelInfoTranslation translation(Long id, Long travelInfoId, String languageCode,
                                              String title, String content) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setId(id);
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }
}
