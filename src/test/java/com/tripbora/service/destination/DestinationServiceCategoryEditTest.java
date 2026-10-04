package com.tripbora.service.destination;

import com.tripbora.dto.DestinationDetailDto;
import com.tripbora.dto.DestinationForm;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationSeason;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationServiceCategoryEditTest {

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
        destinationService = new DestinationService(destinationMapper, destinationImageService,
                bookmarkMapper, amenityService, destinationCommentService, courseService,
                accommodationInfoService, attractionInfoService, restaurantInfoService,
                activityInfoService, shopInfoService,
                new DestinationLocalizationService(destinationMapper));
    }

    @Test
    void editFormRestoresExistingCategorySelections() {
        Destination destination = destination(9L);
        when(destinationMapper.findDestinationDetail(9L)).thenReturn(destination);
        when(destinationMapper.findTranslationsByDestinationId(9L))
                .thenReturn(List.of(koreanTranslation(9L)));
        when(destinationMapper.findImagesByDestinationId(9L)).thenReturn(List.of());
        when(amenityService.getAttractionAmenities(eq(9L), any())).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));
        when(destinationMapper.findMainCategoryId(9L)).thenReturn(20L);

        DestinationDetailDto detail = destinationService.getDestinationDetailWithInfo(9L);
        DestinationForm form = DestinationForm.fromDetailDto(detail, List.of());

        assertThat(form.getCategoryIds()).containsExactly(10L, 20L);
        // 수정 폼은 저장된 대표를 그대로 보여준다(가장 작은 ID가 아니어도).
        assertThat(form.getMainCategoryId()).isEqualTo(20L);
    }

    @Test
    void unchangedCategorySelectionDoesNotRewriteExistingLinks() {
        DestinationForm form = editForm(List.of(10L, 20L));
        form.setMainCategoryId(10L);
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));
        when(destinationMapper.findMainCategoryId(9L)).thenReturn(10L);

        destinationService.updateDestination(9L, form);

        verify(destinationMapper, never()).insertDestinationCategory(any(), any());
        verify(destinationMapper, never()).deleteDestinationCategory(any(), any());
        verify(destinationMapper, never()).clearMainCategory(any());
        verify(destinationMapper, never()).markMainCategory(any(), any());
    }

    @Test
    void changingOnlyTheMainCategoryUpdatesTheFlagWithoutTouchingLinks() {
        DestinationForm form = editForm(List.of(10L, 20L, 30L));
        form.setMainCategoryId(20L);
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L, 30L));
        when(destinationMapper.findMainCategoryId(9L)).thenReturn(10L);
        when(destinationMapper.markMainCategory(9L, 20L)).thenReturn(1);

        destinationService.updateDestination(9L, form);

        // 기존 대표를 먼저 지워야 여행지당 대표 1개 UNIQUE 와 부딪히지 않는다.
        InOrder order = inOrder(destinationMapper);
        order.verify(destinationMapper).clearMainCategory(9L);
        order.verify(destinationMapper).markMainCategory(9L, 20L);
        verify(destinationMapper, never()).insertDestinationCategory(any(), any());
        verify(destinationMapper, never()).deleteDestinationCategory(any(), any());
    }

    @Test
    void removingTheMainCategoryMakesTheSmallestRemainingCategoryTheMain() {
        // 대표였던 10 을 해제하고 30 을 추가했다. 화면이 대표를 보내지 않아도 남은 것 중 가장 작은 20 이 대표다.
        DestinationForm form = editForm(List.of(30L, 20L));
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));
        // 10 의 연결 행을 지우면 대표 표시도 함께 사라진다.
        when(destinationMapper.findMainCategoryId(9L)).thenReturn(null);
        when(destinationMapper.markMainCategory(9L, 20L)).thenReturn(1);

        destinationService.updateDestination(9L, form);

        verify(destinationMapper).deleteDestinationCategory(9L, 10L);
        verify(destinationMapper).insertDestinationCategory(9L, 30L);
        verify(destinationMapper, never()).deleteDestinationCategory(9L, 20L);
        verify(destinationMapper, never()).insertDestinationCategory(9L, 20L);
        verify(destinationMapper).markMainCategory(9L, 20L);
    }

    @Test
    void aMainCategoryThatWasNotSelectedIsRejectedBeforeAnythingIsSaved() {
        DestinationForm form = editForm(List.of(10L, 20L));
        form.setMainCategoryId(30L);
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));

        assertThatThrownBy(() -> destinationService.updateDestination(9L, form))
                .isInstanceOf(InvalidMainCategoryException.class)
                .hasMessage(InvalidMainCategoryException.MESSAGE);
        verify(destinationMapper, never()).updateDestination(any());
        verify(destinationMapper, never()).insertDestinationCategory(any(), any());
        verify(destinationMapper, never()).deleteDestinationCategory(any(), any());
        verify(destinationMapper, never()).markMainCategory(any(), any());

        DestinationForm create = editForm(List.of(10L));
        create.setMainCategoryId(20L);
        assertThatThrownBy(() -> destinationService.registerDestination(create, 7L))
                .isInstanceOf(InvalidMainCategoryException.class);
        verify(destinationMapper, never()).insertDestination(any());
    }

    @Test
    void aMissingMainLinkFailsSoTheTransactionRollsBack() {
        DestinationForm form = editForm(List.of(10L, 20L));
        form.setMainCategoryId(20L);
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));
        when(destinationMapper.findMainCategoryId(9L)).thenReturn(10L);
        when(destinationMapper.markMainCategory(9L, 20L)).thenReturn(0);

        assertThatThrownBy(() -> destinationService.updateDestination(9L, form))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clearingAllCategoriesRemovesEveryExistingLink() {
        DestinationForm form = editForm(List.of());
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));

        destinationService.updateDestination(9L, form);

        verify(destinationMapper).deleteDestinationCategory(9L, 10L);
        verify(destinationMapper).deleteDestinationCategory(9L, 20L);
        verify(destinationMapper, never()).insertDestinationCategory(any(), any());
        // 연결이 모두 사라지면 대표도 없다.
        verify(destinationMapper, never()).markMainCategory(any(), any());
    }

    @Test
    void createKeepsSavingSelectedCategories() {
        DestinationForm form = editForm(List.of(10L, 20L));
        form.setMainCategoryId(20L);
        givenInsertedDestinationId(99L);
        when(destinationMapper.markMainCategory(99L, 20L)).thenReturn(1);

        Long destinationId = destinationService.registerDestination(form, 7L);

        assertThat(destinationId).isEqualTo(99L);
        verify(destinationMapper).insertDestinationCategory(99L, 10L);
        verify(destinationMapper).insertDestinationCategory(99L, 20L);
        verify(destinationMapper).markMainCategory(99L, 20L);
    }

    @Test
    void importedDestinationWithoutAMainCategoryUsesTheSmallestSelectedCategory() {
        // KTO 가져오기(contentId 로 등록)는 대표값을 보내지 않는다.
        DestinationForm form = editForm(List.of(30L, 10L));
        givenInsertedDestinationId(99L);
        when(destinationMapper.markMainCategory(99L, 10L)).thenReturn(1);

        destinationService.registerDestination(form, 7L, "126508");

        verify(destinationMapper).markMainCategory(99L, 10L);
        verify(destinationMapper, never()).clearMainCategory(any());
    }

    @Test
    void createWithoutCategoriesHasNoMainCategory() {
        givenInsertedDestinationId(99L);

        destinationService.registerDestination(editForm(List.of()), 7L);

        verify(destinationMapper, never()).markMainCategory(any(), any());
    }

    private void givenInsertedDestinationId(Long id) {
        doAnswer(invocation -> {
            Destination destination = invocation.getArgument(0);
            destination.setId(id);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));
    }

    private DestinationForm editForm(List<Long> categoryIds) {
        DestinationForm form = new DestinationForm();
        form.setSeason(DestinationSeason.ALL_SEASONS.name());
        form.setType(DestinationType.ATTRACTION);
        form.setTranslations(List.of());
        form.setCategoryIds(categoryIds);
        return form;
    }

    private Destination destination(Long id) {
        Destination destination = new Destination();
        destination.setId(id);
        destination.setSeason(DestinationSeason.ALL_SEASONS);
        destination.setType(DestinationType.ATTRACTION);
        return destination;
    }

    private DestinationTranslation koreanTranslation(Long destinationId) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode("ko");
        translation.setName("여행지");
        translation.setShortDescription("한줄 소개");
        translation.setDescription("상세 설명");
        return translation;
    }
}
