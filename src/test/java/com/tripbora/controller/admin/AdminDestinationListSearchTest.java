package com.tripbora.controller.admin;

import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.info.AccommodationInfoService;
import com.tripbora.service.info.ShopInfoService;
import com.tripbora.service.info.ActivityInfoService;
import com.tripbora.service.info.AttractionInfoService;
import com.tripbora.service.info.RestaurantInfoService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
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
    @Mock
    private RestaurantInfoService restaurantInfoService;
    @Mock
    private AttractionInfoService attractionInfoService;
    @Mock
    private AccommodationInfoService accommodationInfoService;
    @Mock
    private ActivityInfoService activityInfoService;
    @Mock
    private ShopInfoService shopInfoService;

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
                destinationSaveOrchestrationService,
                restaurantInfoService,
                attractionInfoService,
                accommodationInfoService,
                activityInfoService,
                shopInfoService);

        when(countryCategoryService.getKoreaRootId()).thenReturn(KOREA_ID);
        when(countryCategoryService.getAllRegionIdsUnder(KOREA_ID)).thenReturn(List.of(KOREA_ID, 10L));
        when(countryCategoryService.getAllRegionIdsUnder(ASIA_ID)).thenReturn(List.of(ASIA_ID, 20L));
        when(countryCategoryService.getOverseasRootIds()).thenReturn(List.of(ASIA_ID));
        when(countryCategoryService.getRegionsByDepth(1)).thenReturn(List.of());
    }

    private static final List<Long> ALL_REGION_IDS = List.of(KOREA_ID, 10L, ASIA_ID, 20L);

    @Test
    void keywordIsTrimmedAndPassedToTheListQuery() {
        Model model = list("all", "  경복  ");

        verify(destinationService).getAdminDestinationPage(ALL_REGION_IDS, null, "경복", "latest", 0, 30);
        assertThat(model.getAttribute("keyword")).isEqualTo("경복");
    }

    @Test
    void blankKeywordMeansNoSearchCondition() {
        list("all", "   ");

        verify(destinationService).getAdminDestinationPage(ALL_REGION_IDS, null, null, "latest", 0, 30);
    }

    @Test
    void missingKeywordMeansNoSearchCondition() {
        Model model = list("all", null);

        verify(destinationService).getAdminDestinationPage(ALL_REGION_IDS, null, null, "latest", 0, 30);
        assertThat(model.getAttribute("keyword")).isNull();
    }

    @Test
    void domesticFilterAndKeywordApplyTogether() {
        Model model = list("domestic", "테스트");

        verify(destinationService).getAdminDestinationPage(List.of(KOREA_ID, 10L), null, "테스트", "latest", 0, 30);
        assertThat(model.getAttribute("type")).isEqualTo("domestic");
        assertThat(model.getAttribute("keyword")).isEqualTo("테스트");
    }

    @Test
    void overseasFilterAndKeywordApplyTogether() {
        Model model = list("overseas", "타워");

        verify(destinationService).getAdminDestinationPage(List.of(ASIA_ID, 20L), null, "타워", "latest", 0, 30);
        assertThat(model.getAttribute("type")).isEqualTo("overseas");
        assertThat(model.getAttribute("keyword")).isEqualTo("타워");
    }

    /** 기본은 최신 등록순이고, 알 수 없는 분류·정렬 값은 조건 없음으로 본다. */
    @Test
    void defaultSortIsLatestAndUnknownTypeOrSortIsIgnored() {
        Model model = list(null, null, "UNKNOWN", "random", null);

        verify(destinationService).getAdminDestinationPage(ALL_REGION_IDS, null, null, "latest", 0, 30);
        assertThat(model.getAttribute("sort")).isEqualTo("latest");
        assertThat(model.getAttribute("destinationType")).isNull();
        assertThat(model.getAttribute("listUrl")).isEqualTo("/admin/destinations");
    }

    /** 범위·분류·검색·정렬은 함께 걸리고, 쪽을 넘겨도 조건이 쪽 이동 주소에 그대로 남는다. */
    @Test
    void scopeTypeKeywordSortAndPageApplyTogetherAndStayInPageLinks() {
        when(destinationService.countAdminDestinations(List.of(KOREA_ID, 10L), "ACCOMMODATION", "호텔"))
                .thenReturn(95);

        Model model = list("domestic", "호텔", "ACCOMMODATION", "name", "3");

        verify(destinationService).getAdminDestinationPage(
                List.of(KOREA_ID, 10L), "ACCOMMODATION", "호텔", "name", 60, 30);
        assertThat(model.getAttribute("currentPage")).isEqualTo(3);
        assertThat(model.getAttribute("totalPages")).isEqualTo(4);
        assertThat(model.getAttribute("pageOffset")).isEqualTo(60L);
        assertThat((String) model.getAttribute("listUrl"))
                .startsWith("/admin/destinations?")
                .contains("scope=domestic", "destinationType=ACCOMMODATION", "keyword=%ED%98%B8%ED%85%94", "sort=name")
                .doesNotContain("page=");
        assertThat((String) model.getAttribute("listQuery")).contains("page=3");
    }

    /** 범위 밖 쪽 번호는 마지막 쪽으로 맞춘다(삭제 후 마지막 쪽이 비는 경우 포함). */
    @Test
    void pageBeyondTheLastIsClampedToTheLastPage() {
        when(destinationService.countAdminDestinations(ALL_REGION_IDS, null, null)).thenReturn(31);

        Model model = list(null, null, null, null, "9");

        verify(destinationService).getAdminDestinationPage(ALL_REGION_IDS, null, null, "latest", 30, 30);
        assertThat(model.getAttribute("currentPage")).isEqualTo(2);
    }

    @Test
    void deleteReturnsToTheSameFilteredListPage() {
        String redirect = controller.deleteDestination(7L, "domestic", "CAFE", null, null, null, null, null,
                " 카페 ", "oldest", "2");

        verify(destinationService).deleteById(7L);
        assertThat(redirect)
                .startsWith("redirect:/admin/destinations?")
                .contains("scope=domestic", "destinationType=CAFE", "sort=oldest", "page=2")
                .contains("keyword=%EC%B9%B4%ED%8E%98");
    }

    private Model list(String type, String keyword) {
        return list(type, keyword, null, null, null);
    }

    private Model list(String scope, String keyword, String destinationType, String sort, String page) {
        Model model = new ExtendedModelMap();
        controller.showDestinationList(scope, destinationType, null, null, null, null, null,
                keyword, sort, page, model);
        return model;
    }
}
