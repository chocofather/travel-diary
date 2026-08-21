package com.example.travlediary.controller.admin;

import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 여행지 목록 검색 계약.
 * 여행지명 검색과 전체/국내/해외 필터는 서로 덮어쓰지 않고 함께 적용된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDestinationListSearchTest {

    private static final Long KOREA_ID = 1L;
    private static final Long ASIA_ID = 2L;

    @Mock
    private DestinationService destinationService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private AmenityService amenityService;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private DestinationSaveOrchestrationService destinationSaveOrchestrationService;

    private AdminDestinationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminDestinationController(
                destinationService,
                categoryService,
                amenityService,
                countryCategoryService,
                new KtoSelectedPhotoRequestParser(
                        new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator()),
                destinationSaveOrchestrationService);

        when(countryCategoryService.getKoreaRootId()).thenReturn(KOREA_ID);
        when(countryCategoryService.getAllRegionIdsUnder(KOREA_ID)).thenReturn(List.of(KOREA_ID, 10L));
        when(countryCategoryService.getAllRegionIdsUnder(ASIA_ID)).thenReturn(List.of(ASIA_ID, 20L));
        when(countryCategoryService.getOverseasRootIds()).thenReturn(List.of(ASIA_ID));
        when(countryCategoryService.getRegionsByDepth(1)).thenReturn(List.of());
    }

    @Test
    void keywordIsTrimmedAndPassedToTheListQuery() {
        Model model = list("all", "  경복  ");

        verify(destinationService).getDestinationsByRegionIds(List.of(KOREA_ID, 10L, ASIA_ID, 20L), "경복");
        assertThat(model.getAttribute("keyword")).isEqualTo("경복");
    }

    @Test
    void blankKeywordMeansNoSearchCondition() {
        list("all", "   ");

        verify(destinationService)
                .getDestinationsByRegionIds(List.of(KOREA_ID, 10L, ASIA_ID, 20L), null);
    }

    @Test
    void missingKeywordMeansNoSearchCondition() {
        Model model = list("all", null);

        verify(destinationService)
                .getDestinationsByRegionIds(List.of(KOREA_ID, 10L, ASIA_ID, 20L), null);
        assertThat(model.getAttribute("keyword")).isNull();
    }

    @Test
    void domesticFilterAndKeywordApplyTogether() {
        Model model = list("domestic", "테스트");

        verify(destinationService).getDestinationsByRegionIds(List.of(KOREA_ID, 10L), "테스트");
        assertThat(model.getAttribute("type")).isEqualTo("domestic");
        assertThat(model.getAttribute("keyword")).isEqualTo("테스트");
    }

    @Test
    void overseasFilterAndKeywordApplyTogether() {
        Model model = list("overseas", "타워");

        verify(destinationService).getDestinationsByRegionIds(List.of(ASIA_ID, 20L), "타워");
        assertThat(model.getAttribute("type")).isEqualTo("overseas");
        assertThat(model.getAttribute("keyword")).isEqualTo("타워");
    }

    private Model list(String type, String keyword) {
        Model model = new ExtendedModelMap();
        controller.showDestinationList(type, null, null, null, null, null, keyword, model);
        return model;
    }
}
