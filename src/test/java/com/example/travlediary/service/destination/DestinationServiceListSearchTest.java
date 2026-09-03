package com.example.travlediary.service.destination;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 목록 조회가 일반 이름 검색과 초성 검색을 각각 Mapper 조건으로 전달하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationServiceListSearchTest {

    private static final List<Long> REGION_IDS = List.of(1L, 10L);

    @Mock
    private DestinationMapper destinationMapper;
    @Mock
    private DestinationImageService destinationImageService;
    @Mock
    private BookmarkMapper bookmarkMapper;
    @Mock
    private AmenityService amenityService;
    @Mock
    private DestinationCommentService destinationCommentService;
    @Mock
    private AccommodationInfoService accommodationInfoService;
    @Mock
    private AttractionInfoService attractionInfoService;
    @Mock
    private RestaurantInfoService restaurantInfoService;
    @Mock
    private ActivityInfoService activityInfoService;
    @Mock
    private ShopInfoService shopInfoService;
    @Mock
    private CourseService courseService;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(
                destinationMapper, destinationImageService, bookmarkMapper, amenityService,
                destinationCommentService, courseService,
                accommodationInfoService, attractionInfoService,
                restaurantInfoService, activityInfoService, shopInfoService,
                new DestinationLocalizationService(destinationMapper));
    }

    @Test
    void ordinaryKeywordIsSentAsAPartialNameCondition() {
        destinationService.getDestinationsByRegionIds(REGION_IDS, "경복");

        verify(destinationMapper).findByRegionIds(REGION_IDS, "경복", null);
    }

    @Test
    void chosungKeywordIsSentAsAGeneratedPatternInsteadOfTheRawJamo() {
        destinationService.getDestinationsByRegionIds(REGION_IDS, "ㄱㅂㄱ");

        ArgumentCaptor<String> chosung = ArgumentCaptor.forClass(String.class);
        verify(destinationMapper)
                .findByRegionIds(org.mockito.ArgumentMatchers.eq(REGION_IDS),
                        org.mockito.ArgumentMatchers.isNull(),
                        chosung.capture());
        assertThat(Pattern.compile(chosung.getValue()).matcher("경복궁").find()).isTrue();
        assertThat(Pattern.compile(chosung.getValue()).matcher("창덕궁").find()).isFalse();
    }

    @Test
    void blankKeywordCarriesNoSearchCondition() {
        destinationService.getDestinationsByRegionIds(REGION_IDS, "   ");

        verify(destinationMapper).findByRegionIds(REGION_IDS, null, null);
    }
}
