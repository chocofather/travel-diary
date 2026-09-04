package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.RestaurantInfoTranslationForm;
import com.example.travlediary.model.Destination;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 저장 경로가 식당 상세정보의 언어별 값을 함께 넘기는지 본다.
 *
 * <p>원본(restaurant_info) 저장은 예전 그대로이며, 번역 저장은 그 뒤에 이어진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationRestaurantAdminSaveTest {

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
    }

    @Test
    void registeringARestaurantSavesTheBaseThenTheLanguageSlots() {
        DestinationForm form = restaurantForm();
        doAnswerWithGeneratedId();

        destinationService.registerDestination(form, 7L);

        verify(restaurantInfoService).save(form.getRestaurantInfo());
        verify(restaurantInfoService).saveTranslations(
                21L, form.getRestaurantInfo(), form.getRestaurantInfoTranslations());
        // 번역 대상이 아닌 값은 원본에 그대로 실려 나간다
        assertThat(form.getRestaurantInfo().getContactNumber()).isEqualTo("02-1234-5678");
        assertThat(form.getRestaurantInfo().getSeatCount()).isEqualTo(48);
    }

    @Test
    void updatingARestaurantSavesTheBaseThenTheLanguageSlots() {
        DestinationForm form = restaurantForm();
        Destination stored = new Destination();
        stored.setId(21L);
        when(destinationMapper.findById(21L)).thenReturn(stored);
        when(destinationMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(21L)).thenReturn(List.of());

        destinationService.updateDestination(21L, form);

        verify(restaurantInfoService).update(form.getRestaurantInfo());
        verify(restaurantInfoService).saveTranslations(
                21L, form.getRestaurantInfo(), form.getRestaurantInfoTranslations());
    }

    @Test
    void otherDestinationTypesNeverTouchTheRestaurantTranslations() {
        DestinationForm form = restaurantForm();
        form.setType(DestinationType.ATTRACTION);
        form.setRestaurantInfo(null);
        doAnswerWithGeneratedId();

        destinationService.registerDestination(form, 7L);

        verify(restaurantInfoService, never()).saveTranslations(any(), any(), any());
    }

    private void doAnswerWithGeneratedId() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Destination.class).setId(21L);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));
    }

    private DestinationForm restaurantForm() {
        DestinationForm form = new DestinationForm();
        form.setType(DestinationType.RESTAURANTS);
        form.setRegionId(235L);
        form.setSeason("SPRING");
        form.setLatitude(BigDecimal.ONE);
        form.setLongitude(BigDecimal.ONE);

        RestaurantInfo info = new RestaurantInfo();
        info.setMainMenu("비빔밥");
        info.setOpeningHours("11:00~21:00");
        info.setContactNumber("02-1234-5678");
        info.setSeatCount(48);
        form.setRestaurantInfo(info);

        List<RestaurantInfoTranslationForm> slots = form.getRestaurantInfoTranslations();
        assertThat(slots).extracting(RestaurantInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        slots.get(0).setMainMenu("Bibimbap");
        return form;
    }
}
