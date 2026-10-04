package com.tripbora.service.destination;

import com.tripbora.config.i18n.SupportedLanguage;
import com.tripbora.dto.ActivityInfoTranslationForm;
import com.tripbora.dto.DestinationDetailDto;
import com.tripbora.dto.DestinationForm;
import com.tripbora.model.ActivityInfo;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.model.DestinationType;
import com.tripbora.repository.bookmark.BookmarkMapper;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.comment.DestinationCommentService;
import com.tripbora.service.course.CourseService;
import com.tripbora.service.info.AccommodationInfoService;
import com.tripbora.service.info.ActivityInfoService;
import com.tripbora.service.info.AttractionInfoService;
import com.tripbora.service.info.RestaurantInfoService;
import com.tripbora.service.info.ShopInfoService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 체험/액티비티 상세를 어느 경로로 읽고, 저장할 때 번역까지 함께 넘기는지 고정한다.
 *
 * <p>공개 상세는 요청 언어로 읽고, 관리자(원문) 읽기는 예전처럼 번역 없이 읽는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationActivityDetailLocalizationTest {

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
        destination.setId(41L);
        destination.setType(DestinationType.ACTIVITY);
        when(destinationMapper.findDestinationDetail(41L)).thenReturn(destination);
        when(destinationMapper.findTranslationsByDestinationId(41L)).thenReturn(
                List.of(translation("ko", "래프팅 체험"), translation("en", "Rafting")));
        when(amenityService.getActivityAmenities(eq(41L), any())).thenReturn(List.of());
    }

    @Test
    void publicDetailReadsTheActivityInfoInTheRequestedLanguage() {
        ActivityInfo localized = activityInfo("About 2 hours");
        when(activityInfoService.findLocalizedByDestinationId(41L, SupportedLanguage.ENGLISH))
                .thenReturn(localized);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(
                41L, SupportedLanguage.ENGLISH);

        assertThat(dto.getActivityInfo()).isSameAs(localized);
        assertThat(dto.getActivityInfo().getContactNumber()).isEqualTo("02-1234-5678");
        verify(activityInfoService).findLocalizedByDestinationId(41L, SupportedLanguage.ENGLISH);
        verify(activityInfoService, never()).findByDestinationId(41L);
    }

    @Test
    void theBaseReadUsedByAdminScreensStaysOnTheOriginalText() {
        ActivityInfo base = activityInfo("약 2시간");
        when(activityInfoService.findByDestinationId(41L)).thenReturn(base);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(41L);

        assertThat(dto.getActivityInfo()).isSameAs(base);
        verify(activityInfoService).findByDestinationId(41L);
        verify(activityInfoService, never()).findLocalizedByDestinationId(any(), any());
    }

    @Test
    void savingAnActivityPassesTheKoreanBaseAndTheTranslationSlots() {
        DestinationForm form = activityForm();
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Destination.class).setId(41L);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));

        destinationService.registerDestination(form, 7L);

        verify(activityInfoService).save(form.getActivityInfo());
        verify(activityInfoService).saveTranslations(41L, form.getActivityInfo(),
                form.getActivityInfoTranslations());
    }

    @Test
    void updatingAnActivityPassesTheKoreanBaseAndTheTranslationSlots() {
        Destination stored = new Destination();
        stored.setId(41L);
        when(destinationMapper.findById(41L)).thenReturn(stored);
        when(destinationMapper.findCategoryIdsByDestinationId(41L)).thenReturn(List.of());
        when(destinationMapper.findTranslationsByDestinationId(41L)).thenReturn(List.of());

        DestinationForm form = activityForm();
        destinationService.updateDestination(41L, form);

        verify(activityInfoService).update(form.getActivityInfo());
        verify(activityInfoService).saveTranslations(41L, form.getActivityInfo(),
                form.getActivityInfoTranslations());
    }

    private DestinationForm activityForm() {
        DestinationForm form = new DestinationForm();
        form.setType(DestinationType.ACTIVITY);
        form.setRegionId(235L);
        form.setSeason("SPRING");
        form.setLatitude(BigDecimal.ONE);
        form.setLongitude(BigDecimal.ONE);
        form.setActivityInfo(activityInfo("약 2시간"));
        for (ActivityInfoTranslationForm slot : form.getActivityInfoTranslations()) {
            if ("en".equals(slot.getLanguageCode())) {
                slot.setRequiredTime("About 2 hours");
            }
        }
        return form;
    }

    private ActivityInfo activityInfo(String requiredTime) {
        ActivityInfo info = new ActivityInfo();
        info.setDestinationId(41L);
        info.setRequiredTime(requiredTime);
        info.setContactNumber("02-1234-5678");
        return info;
    }

    private DestinationTranslation translation(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(41L);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
