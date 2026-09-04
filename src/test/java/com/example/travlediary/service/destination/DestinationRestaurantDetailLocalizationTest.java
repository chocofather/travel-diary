package com.example.travlediary.service.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.RestaurantInfo;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 식당 상세를 어느 경로로 읽는지 고정한다.
 *
 * <p>공개 상세는 요청 언어로 읽고, 관리자(원문) 읽기는 예전처럼 번역 없이 읽는다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationRestaurantDetailLocalizationTest {

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

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationMapper, destinationImageService,
                bookmarkMapper, amenityService, destinationCommentService, courseService,
                accommodationInfoService, attractionInfoService, restaurantInfoService,
                activityInfoService, shopInfoService,
                new DestinationLocalizationService(destinationMapper));

        Destination destination = new Destination();
        destination.setId(21L);
        destination.setType(DestinationType.RESTAURANTS);
        when(destinationMapper.findDestinationDetail(21L)).thenReturn(destination);
        when(destinationMapper.findTranslationsByDestinationId(21L))
                .thenReturn(List.of(translation("ko", "한식당"), translation("en", "Korean Table")));
        when(amenityService.getRestaurantAmenities(eq(21L), any())).thenReturn(List.of());
    }

    @Test
    void publicDetailReadsTheRestaurantInfoInTheRequestedLanguage() {
        RestaurantInfo localized = restaurantInfo("Bibimbap");
        when(restaurantInfoService.findLocalizedByDestinationId(21L, SupportedLanguage.ENGLISH))
                .thenReturn(localized);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(
                21L, SupportedLanguage.ENGLISH);

        assertThat(dto.getRestaurantInfo()).isSameAs(localized);
        assertThat(dto.getRestaurantInfo().getContactNumber()).isEqualTo("02-1234-5678");
        verify(restaurantInfoService).findLocalizedByDestinationId(21L, SupportedLanguage.ENGLISH);
        verify(restaurantInfoService, never()).findByDestinationId(21L);
    }

    @Test
    void theBaseReadUsedByAdminScreensStaysOnTheOriginalText() {
        RestaurantInfo base = restaurantInfo("비빔밥");
        when(restaurantInfoService.findByDestinationId(21L)).thenReturn(base);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(21L);

        assertThat(dto.getRestaurantInfo()).isSameAs(base);
        verify(restaurantInfoService).findByDestinationId(21L);
        verify(restaurantInfoService, never()).findLocalizedByDestinationId(any(), any());
    }

    private RestaurantInfo restaurantInfo(String mainMenu) {
        RestaurantInfo info = new RestaurantInfo();
        info.setDestinationId(21L);
        info.setMainMenu(mainMenu);
        info.setContactNumber("02-1234-5678");
        return info;
    }

    private DestinationTranslation translation(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(21L);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
