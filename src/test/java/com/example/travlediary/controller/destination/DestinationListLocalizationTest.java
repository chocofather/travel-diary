package com.example.travlediary.controller.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationListLocalizationTest {

    @Mock private DestinationService destinationService;
    @Mock private DestinationImageService destinationImageService;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private ReferenceNameLocalizationService referenceNameLocalizationService;
    @Mock private HttpServletRequest request;

    private DestinationController controller;
    private Destination destination;
    private DestinationDto localizedCard;
    private CountryCategory seoul;
    private CountryCategory jongno;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        controller = new DestinationController(destinationService, destinationImageService,
                countryCategoryService, destinationCommentService,
                referenceNameLocalizationService);
        seoul = region(38L, "서울", 3, 7L);
        jongno = region(235L, "종로구", 4, 38L);
        destination = new Destination();
        destination.setId(15L);
        destination.setRegionId(235L);
        destination.setRegionName("종로구");
        destination.setName("경복궁");
        destination.setShortDescription("한국어 소개");
        localizedCard = new DestinationDto();
        localizedCard.setId(15L);
        localizedCard.setName("Gyeongbokgung Palace");
        localizedCard.setShortDescription("English summary");
        localizedCard.setRegionName("Jongno-gu");

        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));
        when(countryCategoryService.getAllRegionIdsUnder(7L)).thenReturn(List.of(7L, 38L, 235L));
        when(destinationService.getDestinationsByRegionIdsPaged(
                List.of(7L, 38L, 235L), 0, 12, "default"))
                .thenReturn(List.of(destination));
        when(destinationService.countDestinationsByRegionIds(List.of(7L, 38L, 235L)))
                .thenReturn(1);
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                anyMap(), eq(SupportedLanguage.ENGLISH)))
                .thenReturn(Map.of(38L, "Seoul", 235L, "Jongno-gu"));
        when(destinationService.convertToLocalizedDtoWithBookmark(
                eq(List.of(destination)), eq(null), eq(SupportedLanguage.ENGLISH), anyMap()))
                .thenReturn(List.of(localizedCard));
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void initialListLocalizesRegionSelectorAndCurrentPageCards() {
        when(countryCategoryService.getSubregions(7L, 3)).thenReturn(List.of(seoul));

        Model model = new ExtendedModelMap();
        String view = controller.destinationList(
                "domestic", null, 1, 12, "default", null, request, model);

        assertThat(view).isEqualTo("destination/list");
        assertThat(model.getAttribute("regionDisplayNames"))
                .isEqualTo(Map.of(38L, "Seoul", 235L, "Jongno-gu"));
        assertThat(model.getAttribute("destinations")).isEqualTo(List.of(localizedCard));
        verify(destinationService).convertToLocalizedDtoWithBookmark(
                eq(List.of(destination)), eq(null), eq(SupportedLanguage.ENGLISH), anyMap());
    }

    @Test
    void asynchronousListFragmentUsesTheSameCookieLocaleForSubregionsAndHeading() {
        when(countryCategoryService.getById(38L)).thenReturn(seoul);
        when(countryCategoryService.getSubregions(38L, 4)).thenReturn(List.of(jongno));
        when(countryCategoryService.getAllRegionIdsUnder(38L)).thenReturn(List.of(38L, 235L));
        when(destinationService.getDestinationsByRegionIdsPaged(
                List.of(38L, 235L), 0, 12, "default"))
                .thenReturn(List.of(destination));
        when(destinationService.countDestinationsByRegionIds(List.of(38L, 235L))).thenReturn(1);

        Map<Long, String> names = new LinkedHashMap<>();
        names.put(38L, "Seoul");
        names.put(235L, "Jongno-gu");
        when(referenceNameLocalizationService.localizeCountryCategoryNames(
                anyMap(), eq(SupportedLanguage.ENGLISH))).thenReturn(names);

        Model model = new ExtendedModelMap();
        String view = controller.destinationListFragment(
                "domestic", 38L, 1, 12, "default", null, model);

        assertThat(view).isEqualTo("destination/fragment :: destinationList");
        assertThat(model.getAttribute("selectedCityName")).isEqualTo("Seoul");
        assertThat(model.getAttribute("regionDisplayNames")).isEqualTo(names);
        assertThat(model.getAttribute("destinations")).isEqualTo(List.of(localizedCard));
    }

    private CountryCategory region(Long id, String name, int depth, Long parentId) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setDepth(depth);
        region.setParentId(parentId);
        return region;
    }
}
