package com.example.travlediary.service.destination;

import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.course.CourseServiceImpl;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 여행지를 지운 뒤 코스의 STOP 번호를 메꾸는 자리.
 *
 * <p>연결 행은 FK CASCADE 로 사라지지만 남은 STOP 은 예전 번호를 그대로 들고 있다.
 * 어느 코스였는지는 지우기 전에만 알 수 있어, 순서가 곧 정확성이다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationDeletionCourseOrderTest {

    private static final Long DESTINATION_ID = 9L;

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
    private CourseService courseService;
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

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(
                destinationMapper, destinationImageService, bookmarkMapper, amenityService,
                destinationCommentService, courseService,
                accommodationInfoService, attractionInfoService,
                restaurantInfoService, activityInfoService, shopInfoService);
    }

    @Test
    void theAffectedCoursesAreLookedUpBeforeTheDestinationIsGone() {
        givenCoursesHolding(DESTINATION_ID, 10L, 11L);

        destinationService.deleteById(DESTINATION_ID);

        /*
          여행지가 사라지면 연결 행도 CASCADE 로 함께 없어져,
          그다음에는 어느 코스가 영향을 받았는지 알아낼 방법이 없다.
        */
        InOrder order = inOrder(courseService, destinationMapper);
        order.verify(courseService).getCourseIdsContainingDestination(DESTINATION_ID);
        order.verify(destinationMapper).deleteById(DESTINATION_ID);
    }

    @Test
    void theStopsAreTidiedUpOnlyAfterTheDestinationRowIsDeleted() {
        givenCoursesHolding(DESTINATION_ID, 10L);

        destinationService.deleteById(DESTINATION_ID);

        // CASCADE 로 줄이 빠진 뒤라야 남은 STOP 을 셀 수 있다
        InOrder order = inOrder(destinationMapper, courseService);
        order.verify(destinationMapper).deleteById(DESTINATION_ID);
        order.verify(courseService).resequenceStops(List.of(10L));
    }

    @Test
    void theWholeOrderIsLookUpThenDeleteThenTidyUp() {
        givenCoursesHolding(DESTINATION_ID, 10L, 11L);

        destinationService.deleteById(DESTINATION_ID);

        InOrder order = inOrder(courseService, destinationMapper);
        order.verify(courseService).getCourseIdsContainingDestination(DESTINATION_ID);
        order.verify(destinationMapper).deleteById(DESTINATION_ID);
        order.verify(courseService).resequenceStops(List.of(10L, 11L));
    }

    @Test
    void everyCourseThatHeldTheDestinationIsHandedOver() {
        // 한 여행지가 여러 코스에 담겨 있었다. 코스마다 각각 1 부터 다시 센다
        givenCoursesHolding(DESTINATION_ID, 10L, 11L, 12L);

        destinationService.deleteById(DESTINATION_ID);

        verify(courseService).resequenceStops(List.of(10L, 11L, 12L));
    }

    @Test
    void aDestinationInNoCourseIsDeletedJustAsBefore() {
        givenCoursesHolding(DESTINATION_ID);

        destinationService.deleteById(DESTINATION_ID);

        // 지우는 일은 그대로 끝나고, 메꿀 번호가 없다는 것은 코스 쪽이 판단한다
        verify(destinationMapper).deleteById(DESTINATION_ID);
        verify(bookmarkMapper).deleteByTarget(
                BookmarkTargetType.DESTINATION.name(), DESTINATION_ID);
        verify(courseService).resequenceStops(List.of());
    }

    @Test
    void aFailedTidyUpTakesTheDeletionDownWithIt() {
        givenCoursesHolding(DESTINATION_ID, 10L);
        doThrow(new IllegalStateException("코스 여행지 순서 정리에 실패했습니다."))
                .when(courseService).resequenceStops(any());

        /*
          예외를 삼키지 않는다. 여기서 나가야 트랜잭션이 되돌아가고,
          "여행지만 지워지고 번호는 깨진" 절반의 성공이 남지 않는다.
        */
        assertThatThrownBy(() -> destinationService.deleteById(DESTINATION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("코스 여행지 순서 정리에 실패했습니다.");

        // 커밋된 뒤에 하는 파일 정리까지 가지 않는다
        verify(destinationImageService, never()).deleteFilesAfterCommit(any());
        verify(destinationCommentService, never()).deleteImageFilesAfterCommit(any());
    }

    @Test
    void theTidyUpSharesTheDeletionTransaction() throws NoSuchMethodException {
        /*
          코스 쪽 정리가 같은 트랜잭션에 참여해야 함께 되돌아간다.
          별도 트랜잭션(REQUIRES_NEW)을 열면 삭제가 취소돼도 번호만 바뀐 채로 남는다.
        */
        Method deleteById = DestinationService.class.getMethod("deleteById", Long.class);
        assertThat(deleteById.getAnnotation(Transactional.class)).isNotNull();

        assertJoinsTheCallersTransaction(
                CourseServiceImpl.class.getMethod("getCourseIdsContainingDestination", Long.class));
        assertJoinsTheCallersTransaction(
                CourseServiceImpl.class.getMethod("resequenceStops", List.class));
    }

    /** 기본 propagation(REQUIRED) 이라야 이미 열려 있는 트랜잭션에 합류한다. */
    private void assertJoinsTheCallersTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).as("%s", method.getName()).isNotNull();
        assertThat(transactional.propagation())
                .as("%s", method.getName())
                .isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void theFilesAreStillCleanedUpOnlyAfterTheDeletionWentThrough() {
        givenCoursesHolding(DESTINATION_ID, 10L);

        destinationService.deleteById(DESTINATION_ID);

        // 파일 정리는 예전 그대로 맨 끝이다. 번호 메꾸기가 그 앞을 가로채지 않는다
        InOrder order = inOrder(courseService, destinationImageService);
        order.verify(courseService).resequenceStops(List.of(10L));
        order.verify(destinationImageService).deleteFilesAfterCommit(any());
    }

    private void givenCoursesHolding(Long destinationId, Long... courseIds) {
        when(destinationMapper.findImagesByDestinationId(destinationId)).thenReturn(List.of());
        when(destinationCommentService.findAllCommentImageUrls(destinationId))
                .thenReturn(List.of());
        when(courseService.getCourseIdsContainingDestination(destinationId))
                .thenReturn(List.of(courseIds));
    }
}
