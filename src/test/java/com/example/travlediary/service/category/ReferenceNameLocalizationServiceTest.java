package com.example.travlediary.service.category;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryTranslation;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.CountryCategoryTranslation;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceNameLocalizationServiceTest {

    @Mock
    private CountryCategoryMapper countryCategoryMapper;
    @Mock
    private CategoryMapper categoryMapper;

    private ReferenceNameLocalizationService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceNameLocalizationService(
                countryCategoryMapper, categoryMapper, new LocalizedReferenceNameResolver());
    }

    @Test
    void localizesMultipleRegionsWithOneBatchQuery() {
        CountryCategory seoul = countryCategory(38L, "서울");
        CountryCategory jongno = countryCategory(235L, "종로구");
        when(countryCategoryMapper.findTranslationsByCountryCategoryIds(List.of(38L, 235L)))
                .thenReturn(List.of(
                        countryTranslation(1L, 38L, "ko", "서울"),
                        countryTranslation(2L, 38L, "en", "Seoul"),
                        countryTranslation(3L, 235L, "ko", "종로구"),
                        countryTranslation(4L, 235L, "en", "Jongno-gu")));

        Map<Long, String> names = service.localizeCountryCategories(
                List.of(seoul, jongno), SupportedLanguage.ENGLISH);

        assertThat(names).containsEntry(38L, "Seoul").containsEntry(235L, "Jongno-gu");
        verify(countryCategoryMapper).findTranslationsByCountryCategoryIds(List.of(38L, 235L));
    }

    @Test
    void localizesRegionBaseNamesCollectedFromAListWithoutReloadingBaseRows() {
        when(countryCategoryMapper.findTranslationsByCountryCategoryIds(List.of(38L, 235L)))
                .thenReturn(List.of(
                        countryTranslation(1L, 38L, "ko", "서울"),
                        countryTranslation(2L, 38L, "ja", "ソウル"),
                        countryTranslation(3L, 235L, "ko", "종로구"),
                        countryTranslation(4L, 235L, "ja", "鐘路区")));

        Map<Long, String> baseNames = new LinkedHashMap<>();
        baseNames.put(38L, "서울");
        baseNames.put(235L, "종로구");

        Map<Long, String> names = service.localizeCountryCategoryNames(
                baseNames, SupportedLanguage.JAPANESE);

        assertThat(names).containsEntry(38L, "ソウル").containsEntry(235L, "鐘路区");
        verify(countryCategoryMapper).findTranslationsByCountryCategoryIds(List.of(38L, 235L));
    }

    @ParameterizedTest
    @MethodSource("seoulNames")
    void regionSelectorUsesEachSupportedLocale(
            SupportedLanguage language, String expectedSeoul, String expectedJongno) {
        when(countryCategoryMapper.findTranslationsByCountryCategoryIds(List.of(38L, 235L)))
                .thenReturn(List.of(
                        countryTranslation(1L, 38L, "ko", "서울"),
                        countryTranslation(2L, 38L, "en", "Seoul"),
                        countryTranslation(3L, 38L, "ja", "ソウル"),
                        countryTranslation(4L, 38L, "zh-CN", "首尔"),
                        countryTranslation(5L, 38L, "zh-TW", "首爾"),
                        countryTranslation(6L, 235L, "ko", "종로구"),
                        countryTranslation(7L, 235L, "en", "Jongno-gu"),
                        countryTranslation(8L, 235L, "ja", "鐘路区"),
                        countryTranslation(9L, 235L, "zh-CN", "钟路区"),
                        countryTranslation(10L, 235L, "zh-TW", "鐘路區")));

        Map<Long, String> baseNames = new LinkedHashMap<>();
        baseNames.put(38L, "서울");
        baseNames.put(235L, "종로구");

        assertThat(service.localizeCountryCategoryNames(baseNames, language))
                .containsEntry(38L, expectedSeoul)
                .containsEntry(235L, expectedJongno);
    }

    @ParameterizedTest
    @EnumSource(value = SupportedLanguage.class, names = {
            "JAPANESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL"
    })
    void missingRequestedRegionTranslationFallsBackToKorean(SupportedLanguage language) {
        CountryCategory seoul = countryCategory(38L, "서울 원본");
        when(countryCategoryMapper.findTranslationsByCountryCategoryIds(List.of(38L)))
                .thenReturn(List.of(countryTranslation(1L, 38L, "ko", "서울")));

        assertThat(service.localizeCountryCategories(List.of(seoul), language))
                .containsEntry(38L, "서울");
    }

    @Test
    void categoryUsesRequestedTranslationWhenItExists() {
        when(categoryMapper.findByIds(List.of(7L)))
                .thenReturn(List.of(category(7L, "랜드마크")));
        when(categoryMapper.findTranslationsByCategoryIds(List.of(7L)))
                .thenReturn(List.of(
                        categoryTranslation(1L, 7L, "ko", "랜드마크"),
                        categoryTranslation(2L, 7L, "en", "Landmark")));

        assertThat(service.localizeCategories(List.of(7L), SupportedLanguage.ENGLISH))
                .containsEntry(7L, "Landmark");
    }

    @ParameterizedTest
    @MethodSource("landmarkNames")
    void categoryDisplaySupportsEveryConfiguredLocale(
            SupportedLanguage language, String expectedName) {
        when(categoryMapper.findByIds(List.of(7L)))
                .thenReturn(List.of(category(7L, "랜드마크")));
        when(categoryMapper.findTranslationsByCategoryIds(List.of(7L)))
                .thenReturn(List.of(
                        categoryTranslation(1L, 7L, "ko", "랜드마크"),
                        categoryTranslation(2L, 7L, "en", "Landmark"),
                        categoryTranslation(3L, 7L, "ja", "ランドマーク"),
                        categoryTranslation(4L, 7L, "zh-CN", "地标"),
                        categoryTranslation(5L, 7L, "zh-TW", "地標")));

        assertThat(service.localizeCategories(List.of(7L), language))
                .containsEntry(7L, expectedName);
    }

    @Test
    void categoryFallsBackToKoreanWhenEnglishRowIsMissing() {
        when(categoryMapper.findByIds(List.of(7L)))
                .thenReturn(List.of(category(7L, "랜드마크 원본")));
        when(categoryMapper.findTranslationsByCategoryIds(List.of(7L)))
                .thenReturn(List.of(categoryTranslation(1L, 7L, "ko", "랜드마크")));

        assertThat(service.localizeCategories(List.of(7L), SupportedLanguage.ENGLISH))
                .containsEntry(7L, "랜드마크");
    }

    @Test
    void blankRequestedAndKoreanNamesUseDeterministicOtherTranslation() {
        when(categoryMapper.findByIds(List.of(7L)))
                .thenReturn(List.of(category(7L, "원본")));
        when(categoryMapper.findTranslationsByCategoryIds(List.of(7L)))
                .thenReturn(List.of(
                        categoryTranslation(30L, 7L, "fr", "Français"),
                        categoryTranslation(20L, 7L, "de", "Deutsch"),
                        categoryTranslation(10L, 7L, "ko", "  "),
                        categoryTranslation(5L, 7L, "en", null)));

        assertThat(service.localizeCategories(List.of(7L), SupportedLanguage.ENGLISH))
                .containsEntry(7L, "Deutsch");
    }

    @Test
    void missingOrBlankTranslationsFallBackToBaseAndThenSafePlaceholder() {
        CountryCategory base = countryCategory(38L, "서울 원본");
        CountryCategory missingBase = countryCategory(39L, null);
        when(countryCategoryMapper.findTranslationsByCountryCategoryIds(List.of(38L, 39L)))
                .thenReturn(List.of(
                        countryTranslation(1L, 38L, "en", " "),
                        countryTranslation(2L, 39L, "ko", null)));

        assertThat(service.localizeCountryCategories(
                List.of(base, missingBase), SupportedLanguage.ENGLISH))
                .containsEntry(38L, "서울 원본")
                .containsEntry(39L, "-");
    }

    @Test
    void emptyCollectionsDoNotIssueQueries() {
        assertThat(service.localizeCountryCategories(List.of(), SupportedLanguage.ENGLISH)).isEmpty();
        assertThat(service.localizeCategories(List.of(), SupportedLanguage.ENGLISH)).isEmpty();

        verify(countryCategoryMapper, never()).findTranslationsByCountryCategoryIds(List.of());
        verify(categoryMapper, never()).findByIds(List.of());
        verify(categoryMapper, never()).findTranslationsByCategoryIds(List.of());
    }

    private CountryCategory countryCategory(Long id, String name) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setRegionName(name);
        return category;
    }

    private static Stream<Arguments> seoulNames() {
        return Stream.of(
                Arguments.of(SupportedLanguage.KOREAN, "서울", "종로구"),
                Arguments.of(SupportedLanguage.ENGLISH, "Seoul", "Jongno-gu"),
                Arguments.of(SupportedLanguage.JAPANESE, "ソウル", "鐘路区"),
                Arguments.of(SupportedLanguage.CHINESE_SIMPLIFIED, "首尔", "钟路区"),
                Arguments.of(SupportedLanguage.CHINESE_TRADITIONAL, "首爾", "鐘路區"));
    }

    private static Stream<Arguments> landmarkNames() {
        return Stream.of(
                Arguments.of(SupportedLanguage.KOREAN, "랜드마크"),
                Arguments.of(SupportedLanguage.ENGLISH, "Landmark"),
                Arguments.of(SupportedLanguage.JAPANESE, "ランドマーク"),
                Arguments.of(SupportedLanguage.CHINESE_SIMPLIFIED, "地标"),
                Arguments.of(SupportedLanguage.CHINESE_TRADITIONAL, "地標"));
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private CountryCategoryTranslation countryTranslation(
            Long id, Long countryCategoryId, String languageCode, String name) {
        CountryCategoryTranslation translation = new CountryCategoryTranslation();
        translation.setId(id);
        translation.setCountryCategoryId(countryCategoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private CategoryTranslation categoryTranslation(
            Long id, Long categoryId, String languageCode, String name) {
        CategoryTranslation translation = new CategoryTranslation();
        translation.setId(id);
        translation.setCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
