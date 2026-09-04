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
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.kto.KtoSelectedPhotoRequestParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 등록 화면이 실제로 그려질 때의 번역 탭.
 *
 * <p>서버 바인딩 이름/인덱스가 그대로인지, 첫 탭만 열려 있는지를 렌더링 결과로 고정한다.
 */
@WebMvcTest(AdminDestinationController.class)
@Import(SecurityConfig.class)
class AdminDestinationTranslationTabsRenderingTest {

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
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void theCreateFormRendersFourLanguageTabsWithOnlyEnglishOpen() throws Exception {
        String body = mockMvc.perform(get("/admin/destinations/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = Jsoup.parse(body);

        // 관광지·숙소·음식점/카페가 같은 탭 구조를 하나씩 쓴다
        var groups = document.select("[data-translation-tabs]");
        assertThat(groups).hasSize(3);

        for (Element group : groups) {
            String label = group.selectFirst("h4").text();
            var tabs = group.select("[data-translation-tab]");
            assertThat(tabs).as(label).hasSize(4);
            assertThat(tabs.eachAttr("data-translation-tab")).as(label)
                    .containsExactly("en", "ja", "zh-CN", "zh-TW");
            assertThat(tabs.eachText()).as(label)
                    .containsExactly("English", "日本語", "简体中文", "繁體中文");
            // 처음에는 영어 탭만 활성
            assertThat(tabs.stream().filter(tab -> tab.hasClass("is-active")).toList()).as(label)
                    .singleElement()
                    .satisfies(tab -> assertThat(tab.attr("data-translation-tab")).isEqualTo("en"));
            assertThat(tabs.eachAttr("aria-selected")).as(label)
                    .containsExactly("true", "false", "false", "false");
            // 관리자 화면 보조 설명은 한국어 그대로
            assertThat(tabs.eachAttr("title")).as(label)
                    .containsExactly("영어", "일본어", "중국어(간체)", "중국어(번체)");

            var panels = group.select("[data-translation-panel]");
            assertThat(panels).as(label).hasSize(4);
            assertThat(panels.get(0).hasAttr("hidden")).as(label).isFalse();
            for (Element hidden : panels.subList(1, panels.size())) {
                assertThat(hidden.hasAttr("hidden"))
                        .as("%s %s", label, hidden.attr("data-translation-panel")).isTrue();
            }
        }
        assertThat(groups.select("h4").eachText())
                .containsExactly("관광지 상세 정보 번역", "숙소 상세 정보 번역",
                        "음식점/카페 상세 정보 번역");
    }

    @Test
    void theRenderedInputsKeepTheServerBindingNamesAndIndexes() throws Exception {
        String body = mockMvc.perform(get("/admin/destinations/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = Jsoup.parse(body);

        for (int index = 0; index < 4; index++) {
            for (String field : List.of("languageCode", "mainMenu", "priceRange",
                    "openingHours", "breakTime", "closedDays", "etc")) {
                String name = "restaurantInfoTranslations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
            for (String field : List.of("languageCode", "closedDays", "openingHours",
                    "admissionFee", "guide")) {
                String name = "attractionInfoTranslations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
            for (String field : List.of("languageCode", "roomType", "etc")) {
                String name = "accommodationInfoTranslations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
        }
        // 숙소 원본(한국어)과 번역하지 않는 값은 그대로다
        assertThat(document.select("[name='accommodationInfo.roomType']")).hasSize(1);
        assertThat(document.select("[name='accommodationInfo.checkinTime']")).hasSize(1);
        assertThat(document.select("[name='accommodationInfo.roomCount']")).hasSize(1);
        assertThat(document.select("[name='accommodationInfo.contactNumber']")).hasSize(1);
        assertThat(document.select("input[name='attractionInfoTranslations[0].languageCode']")
                .attr("value")).isEqualTo("en");
        assertThat(document.select("input[name='attractionInfoTranslations[3].languageCode']")
                .attr("value")).isEqualTo("zh-TW");
        // 관광지 원본(한국어)과 번역하지 않는 값은 그대로다
        assertThat(document.select("[name='attractionInfo.closedDays']")).hasSize(1);
        assertThat(document.select("[name='attractionInfo.contactNumber']")).hasSize(1);
        assertThat(document.select("[name='attractionInfo.parkingAvailable']")).isNotEmpty();
        assertThat(document.select("[name='attractionInfo.homepageUrl']")).hasSize(1);
        // 언어 코드는 화면이 정한 슬롯 값 그대로 실린다
        assertThat(document.select("input[name='restaurantInfoTranslations[0].languageCode']")
                .attr("value")).isEqualTo("en");
        assertThat(document.select("input[name='restaurantInfoTranslations[3].languageCode']")
                .attr("value")).isEqualTo("zh-TW");
        // 한국어 원본 입력은 그대로 남아 있다
        assertThat(document.select("[name='restaurantInfo.mainMenu']")).hasSize(1);
        assertThat(document.select("[name='restaurantInfo.contactNumber']")).hasSize(1);
        // 영문 자동입력 훅은 영어 슬롯에만 붙는다
        var hooks = document.select("[data-kto-tour-english-field]");
        assertThat(hooks).hasSize(3);
        assertThat(hooks.eachAttr("name"))
                .containsExactlyInAnyOrder("restaurantInfoTranslations[0].mainMenu",
                        "restaurantInfoTranslations[0].openingHours",
                        "restaurantInfoTranslations[0].closedDays");
    }
}
