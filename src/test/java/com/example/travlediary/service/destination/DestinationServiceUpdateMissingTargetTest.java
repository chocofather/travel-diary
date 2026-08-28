package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.DestinationTranslationForm;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * stale edit 시나리오:
 * 관리자가 수정 화면을 열어 둔 사이 다른 화면에서 여행지가 삭제되고,
 * 열려 있던 폼을 그대로 저장하는 경우.
 * NPE 500 대신 not-found 로 끝나고 어떤 DB write 도 시작되지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationServiceUpdateMissingTargetTest {

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
                destinationMapper,
                destinationImageService,
                bookmarkMapper,
                amenityService,
                destinationCommentService,
                courseService,
                accommodationInfoService,
                attractionInfoService,
                restaurantInfoService,
                activityInfoService,
                shopInfoService
        );
    }

    @Test
    void updatingADeletedDestinationIsReportedAsNotFoundInsteadOfCrashing() {
        when(destinationMapper.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> destinationService.updateDestination(9L, form()))
                .isInstanceOf(DestinationNotFoundException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    void noFollowUpWriteStartsWhenTheDestinationIsGone() {
        when(destinationMapper.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> destinationService.updateDestination(9L, form()))
                .isInstanceOf(DestinationNotFoundException.class);

        // 존재 확인 외에는 아무 것도 실행하지 않는다
        verify(destinationMapper).findById(9L);
        org.mockito.Mockito.verifyNoMoreInteractions(destinationMapper);
        verifyNoInteractions(
                amenityService,
                attractionInfoService,
                accommodationInfoService,
                restaurantInfoService,
                activityInfoService,
                shopInfoService,
                destinationImageService,
                bookmarkMapper);
    }

    private DestinationForm form() {
        DestinationForm form = new DestinationForm();
        form.setSeason("SPRING");
        form.setRegionId(31L);
        form.setType(DestinationType.ATTRACTION);
        form.setCategoryIds(List.of(4L));
        form.setTranslations(List.of(
                new DestinationTranslationForm("ko", "경복궁", "설명", "요약"),
                new DestinationTranslationForm("en", "Gyeongbokgung", "description", "summary")
        ));
        return form;
    }
}
