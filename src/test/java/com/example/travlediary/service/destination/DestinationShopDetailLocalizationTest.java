package com.example.travlediary.service.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.ShopInfoTranslationForm;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.ShopInfo;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쇼핑 상세를 어느 경로로 읽고, 저장할 때 번역까지 함께 넘기는지 고정한다.
 *
 * <p>공개 상세는 요청 언어로 읽고, 관리자(원문) 읽기는 예전처럼 번역 없이 읽는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationShopDetailLocalizationTest {

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
        destination.setId(51L);
        destination.setType(DestinationType.SHOP);
        when(destinationMapper.findDestinationDetail(51L)).thenReturn(destination);
        when(destinationMapper.findTranslationsByDestinationId(51L)).thenReturn(
                List.of(translation("ko", "남대문시장"), translation("en", "Namdaemun Market")));
        when(amenityService.getShopAmenities(eq(51L), any())).thenReturn(List.of());
    }

    @Test
    void publicDetailReadsTheShopInfoInTheRequestedLanguage() {
        ShopInfo localized = shopInfo("Clothing, accessories, food");
        when(shopInfoService.findLocalizedByDestinationId(51L, SupportedLanguage.ENGLISH))
                .thenReturn(localized);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(
                51L, SupportedLanguage.ENGLISH);

        assertThat(dto.getShopInfo()).isSameAs(localized);
        assertThat(dto.getShopInfo().getContactNumber()).isEqualTo("02-1234-5678");
        verify(shopInfoService).findLocalizedByDestinationId(51L, SupportedLanguage.ENGLISH);
        verify(shopInfoService, never()).findByDestinationId(51L);
    }

    @Test
    void theBaseReadUsedByAdminScreensStaysOnTheOriginalText() {
        ShopInfo base = shopInfo("의류, 소품, 식품");
        when(shopInfoService.findByDestinationId(51L)).thenReturn(base);

        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(51L);

        assertThat(dto.getShopInfo()).isSameAs(base);
        verify(shopInfoService).findByDestinationId(51L);
        verify(shopInfoService, never()).findLocalizedByDestinationId(any(), any());
    }

    @Test
    void savingAShopPassesTheKoreanBaseAndTheTranslationSlots() {
        DestinationForm form = shopForm();
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Destination.class).setId(51L);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));

        destinationService.registerDestination(form, 7L);

        verify(shopInfoService).save(form.getShopInfo());
        verify(shopInfoService).saveTranslations(51L, form.getShopInfo(),
                form.getShopInfoTranslations());
    }

    @Test
    void updatingAShopPassesTheKoreanBaseAndTheTranslationSlots() {
        Destination stored = new Destination();
        stored.setId(51L);
        when(destinationMapper.findById(51L)).thenReturn(stored);
        when(destinationMapper.findCategoryIdsByDestinationId(51L)).thenReturn(List.of());
        when(destinationMapper.findTranslationsByDestinationId(51L)).thenReturn(List.of());

        DestinationForm form = shopForm();
        destinationService.updateDestination(51L, form);

        verify(shopInfoService).update(form.getShopInfo());
        verify(shopInfoService).saveTranslations(51L, form.getShopInfo(),
                form.getShopInfoTranslations());
    }

    private DestinationForm shopForm() {
        DestinationForm form = new DestinationForm();
        form.setType(DestinationType.SHOP);
        form.setRegionId(235L);
        form.setSeason("SPRING");
        form.setLatitude(BigDecimal.ONE);
        form.setLongitude(BigDecimal.ONE);
        form.setShopInfo(shopInfo("의류, 소품, 식품"));
        for (ShopInfoTranslationForm slot : form.getShopInfoTranslations()) {
            if ("en".equals(slot.getLanguageCode())) {
                slot.setMainProducts("Clothing, accessories, food");
            }
        }
        return form;
    }

    private ShopInfo shopInfo(String mainProducts) {
        ShopInfo info = new ShopInfo();
        info.setDestinationId(51L);
        info.setMainProducts(mainProducts);
        info.setContactNumber("02-1234-5678");
        return info;
    }

    private DestinationTranslation translation(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(51L);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
