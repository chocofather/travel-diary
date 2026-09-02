package com.example.travlediary.service.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationServiceLocalizedDetailTest {

    @Mock private DestinationMapper destinationMapper;
    @Mock private DestinationImageService destinationImageService;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private AmenityService amenityService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private CourseService courseService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private ActivityInfoService activityInfoService;
    @Mock private ShopInfoService shopInfoService;

    @InjectMocks private DestinationService destinationService;

    @BeforeEach
    void setUpBaseDetail() {
        Destination destination = new Destination();
        destination.setId(15L);
        destination.setType(DestinationType.ATTRACTION);
        when(destinationMapper.findDestinationDetail(15L)).thenReturn(destination);
    }

    @Test
    void koreanRequestUsesKoreanTranslation() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "경복궁", "한국어 요약", "한국어 설명"),
                translation(2L, "en", "Gyeongbokgung Palace", "English summary", "English description")));

        Destination destination = detail(SupportedLanguage.KOREAN);

        assertThat(destination.getName()).isEqualTo("경복궁");
        assertThat(destination.getShortDescription()).isEqualTo("한국어 요약");
        assertThat(destination.getDescription()).isEqualTo("한국어 설명");
    }

    @Test
    void englishRequestUsesEnglishTranslationWhenPresent() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "경복궁", "한국어 요약", "한국어 설명"),
                translation(2L, "en", "Gyeongbokgung Palace", "English summary", "English description")));

        Destination destination = detail(SupportedLanguage.ENGLISH);

        assertThat(destination.getName()).isEqualTo("Gyeongbokgung Palace");
        assertThat(destination.getShortDescription()).isEqualTo("English summary");
        assertThat(destination.getDescription()).isEqualTo("English description");
    }

    @ParameterizedTest
    @EnumSource(value = SupportedLanguage.class, names = {
            "JAPANESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL"
    })
    void untranslatedSupportedLanguageFallsBackToKorean(SupportedLanguage requestedLanguage) {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "경복궁", "한국어 요약", "한국어 설명"),
                translation(2L, "en", "Gyeongbokgung Palace", "English summary", "English description")));

        Destination destination = detail(requestedLanguage);

        assertThat(destination.getName()).isEqualTo("경복궁");
        assertThat(destination.getShortDescription()).isEqualTo("한국어 요약");
        assertThat(destination.getDescription()).isEqualTo("한국어 설명");
    }

    @Test
    void eachNullRequestedFieldFallsBackIndependently() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "경복궁", "한국어 요약", "한국어 설명"),
                translation(2L, "en", "Gyeongbokgung Palace", null, "English description")));

        Destination destination = detail(SupportedLanguage.ENGLISH);

        assertThat(destination.getName()).isEqualTo("Gyeongbokgung Palace");
        assertThat(destination.getShortDescription()).isEqualTo("한국어 요약");
        assertThat(destination.getDescription()).isEqualTo("English description");
    }

    @Test
    void blankRequestedFieldUsesTheSameFieldFallbackPolicy() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(1L, "ko", "경복궁", "한국어 요약", "한국어 설명"),
                translation(2L, "en", "   ", "English summary", "English description")));

        Destination destination = detail(SupportedLanguage.ENGLISH);

        assertThat(destination.getName()).isEqualTo("경복궁");
        assertThat(destination.getShortDescription()).isEqualTo("English summary");
        assertThat(destination.getDescription()).isEqualTo("English description");
    }

    @Test
    void missingRequestedAndKoreanTranslationsUseDeterministicLanguageOrder() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                translation(5L, "zh-TW", "繁體名稱", "繁體摘要", "繁體說明"),
                translation(9L, "en", "English name", "English summary", "English description")));

        Destination destination = detail(SupportedLanguage.JAPANESE);

        assertThat(destination.getName()).isEqualTo("English name");
        assertThat(destination.getShortDescription()).isEqualTo("English summary");
        assertThat(destination.getDescription()).isEqualTo("English description");
    }

    @Test
    void destinationWithoutAnyTranslationUsesExistingNotFoundResult() {
        when(destinationMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of());

        assertThat(destinationService.getDestinationDetailWithInfo(
                15L, SupportedLanguage.ENGLISH)).isNull();
        verify(destinationMapper, never()).findImagesByDestinationId(15L);
    }

    private Destination detail(SupportedLanguage language) {
        when(destinationMapper.findImagesByDestinationId(15L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(15L)).thenReturn(List.of());
        when(amenityService.getAttractionAmenities(15L)).thenReturn(List.of());
        DestinationDetailDto detail = destinationService.getDestinationDetailWithInfo(15L, language);
        assertThat(detail).isNotNull();
        return detail.getDestination();
    }

    private DestinationTranslation translation(Long id, String languageCode, String name,
                                               String shortDescription, String description) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setId(id);
        translation.setDestinationId(15L);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        translation.setShortDescription(shortDescription);
        translation.setDescription(description);
        return translation;
    }
}
