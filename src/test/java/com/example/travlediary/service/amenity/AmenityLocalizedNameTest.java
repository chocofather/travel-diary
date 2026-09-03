package com.example.travlediary.service.amenity;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AmenityDto;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.repository.amenity.AmenityMapper;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상세 화면 편의시설 이름을 화면 언어로 고른다.
 *
 * <p>고르는 차례는 요청 언어 → 한국어 → 남은 언어 → code 이며,
 * 편의시설이 여럿이어도 조회는 한 번이다.
 */
@ExtendWith(MockitoExtension.class)
class AmenityLocalizedNameTest {

    @Mock private AmenityMapper amenityMapper;
    @Mock private FileUploadService fileUploadService;

    private AmenityService amenityService;

    @BeforeEach
    void setUp() {
        amenityService = new AmenityService(amenityMapper, fileUploadService,
                new LocalizedReferenceNameResolver());
    }

    @ParameterizedTest
    @CsvSource({
            "KOREAN, 주차장",
            "ENGLISH, Parking",
            "JAPANESE, 駐車場",
            "CHINESE_SIMPLIFIED, 停车场",
            "CHINESE_TRADITIONAL, 停車場"
    })
    void eachSupportedLanguageShowsItsOwnAmenityName(SupportedLanguage language,
                                                     String expectedName) {
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());

        List<AmenityDto> amenities = amenityService.getAttractionAmenities(15L, language);

        assertThat(amenities).hasSize(1);
        assertThat(amenities.get(0).getName()).isEqualTo(expectedName);
        // 번호·code·아이콘은 그대로다.
        assertThat(amenities.get(0).getId()).isEqualTo(1);
        assertThat(amenities.get(0).getCode()).isEqualTo("PARKING");
        assertThat(amenities.get(0).getIconUrl()).isEqualTo("/uploads/icons/amenities/parking.png");
    }

    @Test
    void simplifiedAndTraditionalChineseNeverBorrowFromEachOther() {
        // 간체 번역만 있는 편의시설
        List<AmenityTranslation> rows = new ArrayList<>(List.of(
                row(1, "PARKING", "/icon.png", 10, "ko", "주차장"),
                row(1, "PARKING", "/icon.png", 11, "zh-CN", "停车场")));
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L)).thenReturn(rows);

        List<AmenityDto> traditional = amenityService.getAttractionAmenities(
                15L, SupportedLanguage.CHINESE_TRADITIONAL);

        // 번체 요청은 간체를 쓰지 않고 한국어로 내려간다.
        assertThat(traditional.get(0).getName()).isEqualTo("주차장");
    }

    @Test
    void missingRequestedLanguageFallsBackToKorean() {
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L))
                .thenReturn(List.of(
                        row(1, "PARKING", "/icon.png", 10, "ko", "주차장"),
                        row(1, "PARKING", "/icon.png", 11, "en", "Parking")));

        List<AmenityDto> amenities = amenityService.getAttractionAmenities(
                15L, SupportedLanguage.JAPANESE);

        assertThat(amenities.get(0).getName()).isEqualTo("주차장");
    }

    @Test
    void missingRequestedAndKoreanUseTheFirstRemainingLanguage() {
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L))
                .thenReturn(List.of(
                        row(1, "PARKING", "/icon.png", 12, "zh-CN", "停车场"),
                        row(1, "PARKING", "/icon.png", 11, "en", "Parking")));

        List<AmenityDto> amenities = amenityService.getAttractionAmenities(
                15L, SupportedLanguage.JAPANESE);

        // 언어 코드 순서로 결정한다. (en < zh-CN)
        assertThat(amenities.get(0).getName()).isEqualTo("Parking");
    }

    @Test
    void amenityWithoutAnyTranslationStillShowsUpWithItsCode() {
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L))
                .thenReturn(List.of(row(7, "WIFI", "/wifi.png", null, null, null)));

        List<AmenityDto> amenities = amenityService.getAttractionAmenities(
                15L, SupportedLanguage.ENGLISH);

        assertThat(amenities).hasSize(1);
        assertThat(amenities.get(0).getId()).isEqualTo(7);
        assertThat(amenities.get(0).getName()).isEqualTo("WIFI");
        assertThat(amenities.get(0).getIconUrl()).isEqualTo("/wifi.png");
    }

    @Test
    void manyAmenitiesAreReadInOneQueryAndKeepTheirOrder() {
        List<AmenityTranslation> rows = new ArrayList<>();
        for (int amenityId = 1; amenityId <= 5; amenityId++) {
            rows.add(row(amenityId, "CODE" + amenityId, "/icon" + amenityId + ".png",
                    amenityId * 10, "ko", "편의시설" + amenityId));
            rows.add(row(amenityId, "CODE" + amenityId, "/icon" + amenityId + ".png",
                    amenityId * 10 + 1, "en", "Amenity " + amenityId));
        }
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L)).thenReturn(rows);

        List<AmenityDto> amenities = amenityService.getAttractionAmenities(
                15L, SupportedLanguage.ENGLISH);

        assertThat(amenities).extracting(AmenityDto::getName)
                .containsExactly("Amenity 1", "Amenity 2", "Amenity 3", "Amenity 4", "Amenity 5");
        assertThat(amenities).extracting(AmenityDto::getId).containsExactly(1, 2, 3, 4, 5);
        // 편의시설마다 다시 읽지 않는다.
        verify(amenityMapper, times(1)).findAttractionAmenityTranslationsByDestinationId(15L);
        verify(amenityMapper, never()).findTranslationsByAmenityId(org.mockito.ArgumentMatchers.anyInt());
        verify(amenityMapper, never()).findTranslation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void everyDestinationTypeUsesItsOwnSingleQuery() {
        when(amenityMapper.findAccommodationAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());
        when(amenityMapper.findRestaurantAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());
        when(amenityMapper.findActivityAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());
        when(amenityMapper.findShopAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());

        assertThat(amenityService.getAccommodationAmenities(15L, SupportedLanguage.ENGLISH))
                .extracting(AmenityDto::getName).containsExactly("Parking");
        assertThat(amenityService.getRestaurantAmenities(15L, SupportedLanguage.JAPANESE))
                .extracting(AmenityDto::getName).containsExactly("駐車場");
        assertThat(amenityService.getActivityAmenities(15L, SupportedLanguage.CHINESE_SIMPLIFIED))
                .extracting(AmenityDto::getName).containsExactly("停车场");
        assertThat(amenityService.getShopAmenities(15L, SupportedLanguage.CHINESE_TRADITIONAL))
                .extracting(AmenityDto::getName).containsExactly("停車場");

        verify(amenityMapper, times(1)).findAccommodationAmenityTranslationsByDestinationId(15L);
        verify(amenityMapper, times(1)).findRestaurantAmenityTranslationsByDestinationId(15L);
        verify(amenityMapper, times(1)).findActivityAmenityTranslationsByDestinationId(15L);
        verify(amenityMapper, times(1)).findShopAmenityTranslationsByDestinationId(15L);
    }

    @Test
    void callersWithoutALanguageKeepTheKoreanNames() {
        when(amenityMapper.findAttractionAmenityTranslationsByDestinationId(15L))
                .thenReturn(fullyTranslatedParking());

        assertThat(amenityService.getAttractionAmenities(15L))
                .extracting(AmenityDto::getName).containsExactly("주차장");
    }

    private List<AmenityTranslation> fullyTranslatedParking() {
        return List.of(
                row(1, "PARKING", "/uploads/icons/amenities/parking.png", 10, "en", "Parking"),
                row(1, "PARKING", "/uploads/icons/amenities/parking.png", 11, "ja", "駐車場"),
                row(1, "PARKING", "/uploads/icons/amenities/parking.png", 12, "ko", "주차장"),
                row(1, "PARKING", "/uploads/icons/amenities/parking.png", 13, "zh-CN", "停车场"),
                row(1, "PARKING", "/uploads/icons/amenities/parking.png", 14, "zh-TW", "停車場"));
    }

    private AmenityTranslation row(Integer amenityId, String code, String iconUrl,
                                   Integer translationId, String languageCode, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setCode(code);
        translation.setIconUrl(iconUrl);
        translation.setId(translationId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
