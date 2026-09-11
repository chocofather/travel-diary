package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TourAPI 일괄 가져오기 화면 계약.
 * 후보 조회 → 선택 → 등록 구조가 화면에 그대로 있어야 하고, 기본 필터는 미등록만이다.
 */
@WebMvcTest(AdminDestinationController.class)
@Import(SecurityConfig.class)
class AdminDestinationKtoImportPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private DestinationService destinationService;
    @MockitoBean private CategoryService categoryService;
    @MockitoBean private AmenityService amenityService;
    @MockitoBean private CountryCategoryService countryCategoryService;
    @MockitoBean private KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser;
    @MockitoBean private DestinationSaveOrchestrationService destinationSaveOrchestrationService;
    @MockitoBean private RestaurantInfoService restaurantInfoService;
    @MockitoBean private AttractionInfoService attractionInfoService;
    @MockitoBean private AccommodationInfoService accommodationInfoService;
    @MockitoBean private ActivityInfoService activityInfoService;
    @MockitoBean private ShopInfoService shopInfoService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void theImportPageStartsOnTheUnregisteredFilterAndOffersSelectionControls() throws Exception {
        var document = Jsoup.parse(render());

        var filters = document.select("[data-kto-import-filter]");
        assertThat(filters.eachAttr("data-kto-import-filter"))
                .containsExactly("ALL", "NEW", "REGISTERED");
        // 기본값은 미등록만. 등록완료 항목은 숨기지 않고 다른 필터에서 볼 수 있다.
        assertThat(filters.stream().filter(filter -> filter.hasClass("active")).toList())
                .singleElement()
                .satisfies(filter ->
                        assertThat(filter.attr("data-kto-import-filter")).isEqualTo("NEW"));

        // 전체/미등록/등록완료 건수를 각 탭에 표시한다 (TourAPI 원본 총건수가 아니다).
        assertThat(document.select("[data-kto-import-count]").eachAttr("data-kto-import-count"))
                .containsExactly("ALL", "NEW", "REGISTERED");

        assertThat(document.select("[data-kto-import-select-all]")).isNotEmpty();
        assertThat(document.select("[data-kto-import-clear]")).isNotEmpty();
        assertThat(document.select("[data-kto-import-selected-count]")).isNotEmpty();
        // 선택 전에는 등록 버튼을 누를 수 없다.
        assertThat(document.selectFirst("[data-kto-import-submit]").hasAttr("disabled")).isTrue();

        // 후보 목록이 표시하는 값
        assertThat(document.select(".admin-kto-import-table thead th").eachText())
                .contains("이미지", "여행지명", "지역", "유형", "contentId", "등록 여부");
    }

    @Test
    void onlyTouristContentTypesAreOfferedAsImportTargets() throws Exception {
        var document = Jsoup.parse(render());

        var options = document.select("[data-kto-import-content-type] option");
        assertThat(options.eachText()).containsExactly("전체", "관광지", "문화시설", "레포츠", "쇼핑");
        // 숙박·음식점·축제/행사는 이번 기능 대상이 아니다.
        assertThat(options.eachAttr("value")).containsExactly("", "12", "14", "28", "38");
    }

    /** 여행지 관리 화면에서 이 기능으로 들어갈 수 있어야 한다. */
    @Test
    void theDestinationListLinksToTheImportPage() throws Exception {
        try (InputStream template = getClass()
                .getResourceAsStream("/templates/admin/destinations/list.html")) {
            String listPage = new String(template.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(listPage)
                    .contains("@{/admin/destinations/kto-import}")
                    .contains("TourAPI 일괄 가져오기");
        }

        Element backLink = Jsoup.parse(render()).selectFirst(".admin-page-actions a");
        assertThat(backLink.attr("href")).isEqualTo("/admin/destinations");
    }

    private String render() throws Exception {
        return mockMvc.perform(get("/admin/destinations/kto-import")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
