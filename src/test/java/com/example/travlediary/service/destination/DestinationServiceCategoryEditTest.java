package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationTranslation;
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

import static org.assertj.core.api.Assertions.assertThat;
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

        DestinationDetailDto detail = destinationService.getDestinationDetailWithInfo(9L);
        DestinationForm form = DestinationForm.fromDetailDto(detail, List.of());

        assertThat(form.getCategoryIds()).containsExactly(10L, 20L);
    }

    @Test
    void unchangedCategorySelectionDoesNotRewriteExistingLinks() {
        DestinationForm form = editForm(List.of(10L, 20L));
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));

        destinationService.updateDestination(9L, form);

        verify(destinationMapper, never()).insertDestinationCategory(any(), any());
        verify(destinationMapper, never()).deleteDestinationCategory(any(), any());
    }

    @Test
    void changedCategorySelectionPersistsTheExactFinalSelection() {
        DestinationForm form = editForm(List.of(20L, 30L));
        when(destinationMapper.findById(9L)).thenReturn(destination(9L));
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of());
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of(10L, 20L));

        destinationService.updateDestination(9L, form);

        verify(destinationMapper).deleteDestinationCategory(9L, 10L);
        verify(destinationMapper).insertDestinationCategory(9L, 30L);
        verify(destinationMapper, never()).deleteDestinationCategory(9L, 20L);
        verify(destinationMapper, never()).insertDestinationCategory(9L, 20L);
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
    }

    @Test
    void createKeepsSavingSelectedCategories() {
        DestinationForm form = editForm(List.of(10L, 20L));
        doAnswer(invocation -> {
            Destination destination = invocation.getArgument(0);
            destination.setId(99L);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));

        Long destinationId = destinationService.registerDestination(form, 7L);

        assertThat(destinationId).isEqualTo(99L);
        verify(destinationMapper).insertDestinationCategory(99L, 10L);
        verify(destinationMapper).insertDestinationCategory(99L, 20L);
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
