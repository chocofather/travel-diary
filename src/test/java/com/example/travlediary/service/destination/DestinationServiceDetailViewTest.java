package com.example.travlediary.service.destination;

import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.destination.DestinationViewDailyMapper;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 여행지 상세 조회 기록: 누적 조회수는 매번, 일별 집계는 '오늘(KST) 처음 본 여행지'일 때만 올린다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationServiceDetailViewTest {

    // 2026-09-29 15:30 UTC = 2026-09-30 00:30 KST. UTC 로 자르면 하루 전 날짜가 된다.
    private static final Clock JUST_AFTER_KST_MIDNIGHT =
            Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneOffset.UTC);

    @Mock private DestinationMapper destinationMapper;
    @Mock private DestinationViewDailyMapper viewDailyMapper;
    @Mock private DestinationImageService destinationImageService;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private AmenityService amenityService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private ActivityInfoService activityInfoService;
    @Mock private ShopInfoService shopInfoService;
    @Mock private CourseService courseService;

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
                shopInfoService,
                new DestinationLocalizationService(destinationMapper));
        ReflectionTestUtils.setField(destinationService, "viewDailyMapper", viewDailyMapper);
        ReflectionTestUtils.setField(destinationService, "viewClock",
                new DestinationViewClock(JUST_AFTER_KST_MIDNIGHT));
    }

    @Test
    void firstViewTodayCountsBothTheTotalAndTheKstDailyView() {
        List<LocalDate> askedDates = new ArrayList<>();

        destinationService.recordDetailView(7L, date -> askedDates.add(date));

        verify(destinationMapper).incrementViewCount(7L);
        // 서버 시간대가 UTC 여도 날짜는 KST 로 자른다.
        assertThat(askedDates).containsExactly(LocalDate.of(2026, 9, 30));
        verify(viewDailyMapper).incrementDailyView(7L, LocalDate.of(2026, 9, 30));
    }

    @Test
    void repeatViewTodayStillCountsTheTotalButNotTheDailyView() {
        destinationService.recordDetailView(7L, date -> false);

        verify(destinationMapper).incrementViewCount(7L);
        verify(viewDailyMapper, never()).incrementDailyView(any(), any());
    }

    @Test
    void dailyViewUpsertUsesTheApplicationDateInOneStatement() throws Exception {
        String xml;
        try (var in = getClass().getResourceAsStream("/mapper/DestinationViewDailyMapper.xml")) {
            xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        String insert = xml.substring(xml.indexOf("<insert id=\"incrementDailyView\""),
                xml.indexOf("</insert>"));

        assertThat(insert.replaceAll("\\s+", " "))
                .contains("INSERT INTO destination_view_daily (destination_id, view_date, view_count)")
                .contains("VALUES (#{destinationId}, #{viewDate}, 1)")
                .contains("ON DUPLICATE KEY UPDATE view_count = view_count + 1");
        assertThat(insert).doesNotContain("CURDATE()", "NOW()");
    }

    @Test
    void bothWritesShareOneServiceTransaction() throws NoSuchMethodException {
        assertThat(DestinationService.class
                .getMethod("recordDetailView", Long.class, Predicate.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
