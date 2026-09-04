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
    @MockitoBean private ActivityInfoService activityInfoService;
    @MockitoBean private ShopInfoService shopInfoService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void everyTabGroupRendersFourLanguagesWithOnlyEnglishOpen() throws Exception {
        String body = mockMvc.perform(get("/admin/destinations/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = Jsoup.parse(body);

        // 기본정보(여행지명 등)와 유형별 상세정보 다섯 가지가 같은 탭 구조를 하나씩 쓴다
        var groups = document.select("[data-translation-tabs]");
        assertThat(groups).hasSize(6);

        for (Element group : groups) {
            Element heading = group.selectFirst("h4") != null
                    ? group.selectFirst("h4")
                    : group.selectFirst("h3");
            String label = heading.text();
            var tabs = group.select("[data-translation-tab]");
            assertThat(tabs).as(label).hasSize(4);
            assertThat(tabs.eachAttr("data-translation-tab")).as(label)
                    .containsExactly("en", "ja", "zh-CN", "zh-TW");
            // 관리자 화면이므로 탭 이름도 한국어다
            assertThat(tabs.eachText()).as(label)
                    .containsExactly("영어", "일본어", "간체", "번체");
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
        assertThat(groups.select("h3, h4").eachText())
                .containsExactly("번역", "관광지 상세 정보 번역", "숙소 상세 정보 번역",
                        "음식점/카페 상세 정보 번역", "체험/액티비티 상세 정보 번역",
                        "쇼핑 상세 정보 번역");
    }

    @Test
    void theKoreanBasicInfoStaysAboveTheTranslationTabsAtFullWidth() throws Exception {
        var document = Jsoup.parse(mockMvc.perform(get("/admin/destinations/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // 좌우 2열 구조는 사라졌다
        assertThat(document.select(".admin-translation-grid")).isEmpty();
        var korean = document.selectFirst(".admin-language-card.is-primary");
        assertThat(korean).isNotNull();
        assertThat(korean.selectFirst("h3").text()).isEqualTo("한국어");
        assertThat(korean.select("[name='translations[0].name']")).hasSize(1);
        assertThat(korean.select("[name='translations[0].shortDescription']")).hasSize(1);
        assertThat(korean.select("[name='translations[0].description']")).hasSize(1);

        // 번역 탭은 한국어 원본 아래에 온다
        var basicTabs = document.select("[data-translation-tabs]").first();
        assertThat(korean.elementSiblingIndex()).isLessThan(basicTabs.elementSiblingIndex());
        for (int index = 1; index <= 4; index++) {
            for (String field : List.of("languageCode", "name", "shortDescription", "description")) {
                String name = "translations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
        }
        assertThat(document.select("input[name='translations[1].languageCode']").attr("value"))
                .isEqualTo("en");
        assertThat(document.select("input[name='translations[4].languageCode']").attr("value"))
                .isEqualTo("zh-TW");
        // TourAPI 영문 자동입력은 영어 탭 입력칸에 붙는다
        assertThat(document.select("[data-kto-tour-english-name]").attr("name"))
                .isEqualTo("translations[1].name");
        assertThat(document.select("[data-kto-tour-english-overview]").attr("name"))
                .isEqualTo("translations[1].description");
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
            for (String field : List.of("languageCode", "openingHours", "requiredTime",
                    "admissionFee", "ageLimit", "guide")) {
                String name = "activityInfoTranslations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
            for (String field : List.of("languageCode", "closedDays", "openingHours",
                    "mainProducts", "guide")) {
                String name = "shopInfoTranslations[" + index + "]." + field;
                assertThat(document.select("[name='" + name + "']")).as(name).hasSize(1);
            }
        }
        assertThat(document.select("input[name='shopInfoTranslations[0].languageCode']")
                .attr("value")).isEqualTo("en");
        assertThat(document.select("input[name='shopInfoTranslations[3].languageCode']")
                .attr("value")).isEqualTo("zh-TW");
        // 쇼핑 원본(한국어)과 번역하지 않는 값은 그대로다
        assertThat(document.select("[name='shopInfo.mainProducts']")).hasSize(1);
        assertThat(document.select("[name='shopInfo.parkingAvailable']")).isNotEmpty();
        assertThat(document.select("[name='shopInfo.contactNumber']")).hasSize(1);
        assertThat(document.select("[name='shopInfo.homepageUrl']")).hasSize(1);
        assertThat(document.select("input[name='activityInfoTranslations[0].languageCode']")
                .attr("value")).isEqualTo("en");
        assertThat(document.select("input[name='activityInfoTranslations[3].languageCode']")
                .attr("value")).isEqualTo("zh-TW");
        // 체험/액티비티 원본(한국어)과 번역하지 않는 값은 그대로다
        assertThat(document.select("[name='activityInfo.openingHours']")).hasSize(1);
        assertThat(document.select("[name='activityInfo.reservation']")).isNotEmpty();
        assertThat(document.select("[name='activityInfo.equipmentIncluded']")).isNotEmpty();
        assertThat(document.select("[name='activityInfo.parkingAvailable']")).isNotEmpty();
        assertThat(document.select("[name='activityInfo.contactNumber']")).hasSize(1);
        assertThat(document.select("[name='activityInfo.homepageUrl']")).hasSize(1);
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
