package com.example.travlediary.controller.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationAttractionGuideRenderingTest {

    @Mock private DestinationService destinationService;
    @Mock private DestinationImageService destinationImageService;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private ReferenceNameLocalizationService referenceNameLocalizationService;

    private DestinationController controller;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        controller = new DestinationController(destinationService, destinationImageService,
                countryCategoryService, destinationCommentService, referenceNameLocalizationService);
        CountryCategory region = new CountryCategory();
        region.setId(101L);
        region.setRegionName("종로구");
        region.setCode("KR-11-110");
        when(countryCategoryService.getById(101L)).thenReturn(region);
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(101L));
        when(destinationService.getSimilarDestinations(15L, 4)).thenReturn(List.of());
        when(destinationService.convertToDtoWithBookmark(List.of(), null)).thenReturn(List.of());
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void localizedGuideEscapesHtmlBeforeAddingLineBreakMarkup() {
        Model model = render("<script>alert('x')</script>\nSecond line");

        assertThat(model.getAttribute("attractionGuideWithBr"))
                .isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;<br>Second line");
    }

    @Test
    void localizedGuideNormalizesCrLfAndPreservesBlankLines() {
        Model model = render("First\r\nSecond\r\n\r\nThird\nFourth");

        assertThat(model.getAttribute("attractionGuideWithBr"))
                .isEqualTo("First<br>Second<br><br>Third<br>Fourth");
    }

    private Model render(String guide) {
        Destination destination = new Destination();
        destination.setId(15L);
        destination.setRegionId(101L);
        destination.setDescription("description");

        AttractionInfo attractionInfo = new AttractionInfo();
        attractionInfo.setDestinationId(15L);
        attractionInfo.setGuide(guide);

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        dto.setAttractionInfo(attractionInfo);
        dto.setAttractionAmenities(List.of());
        dto.setImages(List.of());
        dto.setCategoryIds(List.of());

        when(destinationService.getDestinationDetailWithInfo(
                eq(15L), any(SupportedLanguage.class))).thenReturn(dto);

        Model model = new ExtendedModelMap();
        controller.destinationDetail(15L, null, model);
        return model;
    }
}
