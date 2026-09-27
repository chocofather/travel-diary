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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    void wikidataPreviewIsOutsideTheDestinationSaveForm() throws Exception {
        var document = Jsoup.parse(mockMvc.perform(get("/admin/destinations/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        var preview = document.selectFirst("[data-wikidata-preview]");
        assertThat(preview).isNotNull();
        assertThat(preview.closest("form")).isNull();
        assertThat(preview.select("[data-wikidata-keyword]")).hasSize(1);
        assertThat(preview.select("[data-wikidata-search]")).hasSize(1);
        assertThat(preview.select("[data-wikidata-detail][hidden]")).hasSize(1);
        assertThat(preview.select("[name]")).isEmpty();
        assertThat(document.select("script[src^='/js/admin-wikidata-form-apply.js']")).hasSize(1);
        assertThat(document.select("script[src^='/js/admin-wikidata-preview.js']")).hasSize(1);
        Element form = document.selectFirst("form[data-translation-collapsible]");
        assertThat(form).isNotNull();
        assertThat(form.select("[name=wikidataQid][type=hidden]")).hasSize(1);
        assertThat(form.select("[data-wikipedia-revision][type=hidden]")).hasSize(5);
    }

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
        // TourAPI 자동입력 훅이 언어별 탭 입력칸에 하나씩 붙는다
        var slotsByLanguage = List.of("en", "ja", "zh-CN", "zh-TW");
        for (int index = 0; index < slotsByLanguage.size(); index++) {
            String languageCode = slotsByLanguage.get(index);
            int slot = index + 1;
            assertThat(document.select("[data-kto-tour-foreign-name='" + languageCode + "']")
                    .attr("name")).as(languageCode).isEqualTo("translations[" + slot + "].name");
            assertThat(document.select("[data-kto-tour-foreign-overview='" + languageCode + "']")
                    .attr("name")).as(languageCode)
                    .isEqualTo("translations[" + slot + "].description");
        }
        // 간단 설명은 자동입력하지 않는다
        assertThat(document.select("[data-kto-tour-foreign-name]")).hasSize(4);
        assertThat(document.select("[data-kto-tour-foreign-overview]")).hasSize(4);
        assertThat(document.select("[name='translations[1].shortDescription']")
                .attr("data-kto-tour-foreign-name")).isEmpty();
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
        // 유형별 자동입력 훅은 언어 슬롯마다 하나씩 붙는다 (5개 유형 12칸 × 4개 언어)
        assertThat(document.select("[data-kto-tour-foreign-field]")).hasSize(48);
        for (String languageCode : List.of("en", "ja", "zh-CN", "zh-TW")) {
            for (var mapping : List.of(
                    List.of("restaurantInfoTranslations", "mainMenu"),
                    List.of("restaurantInfoTranslations", "openingHours"),
                    List.of("restaurantInfoTranslations", "closedDays"),
                    List.of("attractionInfoTranslations", "closedDays"),
                    List.of("attractionInfoTranslations", "openingHours"),
                    List.of("attractionInfoTranslations", "admissionFee"),
                    List.of("accommodationInfoTranslations", "roomType"),
                    List.of("activityInfoTranslations", "openingHours"),
                    List.of("activityInfoTranslations", "admissionFee"),
                    List.of("shopInfoTranslations", "closedDays"),
                    List.of("shopInfoTranslations", "openingHours"),
                    List.of("shopInfoTranslations", "mainProducts"))) {
                String selector = "[data-kto-tour-foreign-field='" + mapping.get(1) + "']"
                        + "[data-kto-tour-foreign-language='" + languageCode + "']"
                        + "[name^='" + mapping.get(0) + "']";
                assertThat(document.select(selector)).as("%s %s %s",
                        languageCode, mapping.get(0), mapping.get(1)).hasSize(1);
            }
        }
        // 대응이 분명하지 않은 칸에는 훅이 없다
        for (String name : List.of("restaurantInfoTranslations[0].priceRange",
                "restaurantInfoTranslations[0].breakTime", "restaurantInfoTranslations[0].etc",
                "attractionInfoTranslations[0].guide", "accommodationInfoTranslations[0].etc",
                "activityInfoTranslations[0].requiredTime",
                "activityInfoTranslations[0].ageLimit", "activityInfoTranslations[0].guide",
                "shopInfoTranslations[0].guide")) {
            assertThat(document.select("[name='" + name + "']").attr("data-kto-tour-foreign-field"))
                    .as(name).isEmpty();
        }
        // 언어 전용 훅은 더 이상 없다
        assertThat(document.select("[data-kto-tour-english-field]")).isEmpty();
    }

    @Test
    void overseasBulkImportPageOffersTypeAndSeasonWithoutPreselectingThem() throws Exception {
        when(countryCategoryService.getKoreaRootId()).thenReturn(7L);
        var document = Jsoup.parse(mockMvc.perform(get("/admin/destinations/wikidata-import")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.selectFirst("[data-wikidata-bulk]").attr("data-domestic-root-id")).isEqualTo("7");
        // 유형·시즌은 임의로 정하지 않는다: 첫 선택지는 빈 값이고 선택된 값이 없다.
        for (String selector : List.of("[data-bulk-common-type]", "[data-bulk-common-season]")) {
            Element select = document.selectFirst(selector);
            assertThat(select.select("option").first().val()).isEmpty();
            assertThat(select.select("option[selected]")).isEmpty();
        }
        assertThat(document.select("[data-bulk-common-type] option")).extracting(Element::val)
                .contains("ATTRACTION", "ACCOMMODATION", "RESTAURANTS", "CAFE", "SHOP", "ACTIVITY");
        assertThat(document.select("[data-bulk-common-season] option")).extracting(Element::val)
                .contains("SPRING", "SUMMER", "FALL", "WINTER", "ALL_SEASONS");
        assertThat(document.select("script[src^='/js/admin-wikidata-bulk-import.js']")).hasSize(1);
        assertThat(document.select("script[src^='/js/admin-commons-photo-picker.js']")).hasSize(1);
        // 국내 일괄 등록과 같은 현재 페이지 선택 도구
        assertThat(document.selectFirst("[data-bulk-page-select]").text()).isEqualTo("현재 페이지 전체선택");
        assertThat(document.selectFirst("[data-bulk-page-clear]").text()).isEqualTo("현재 페이지 선택해제");
        assertThat(document.selectFirst("[data-bulk-clear]").text()).isEqualTo("전체 선택해제");
        assertThat(document.select("th.is-check input[type=checkbox][data-bulk-page-toggle]")).hasSize(1);
        assertThat(document.select("script[src^='/js/admin-bulk-page-selection.js']")).hasSize(1);
    }

    @Test
    void aMainCategoryThatWasNotSelectedIsNotSavedAndIsReportedInTheCategorySection() throws Exception {
        var document = Jsoup.parse(mockMvc.perform(multipart("/admin/destinations")
                        .param("regionId", "101")
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].name", "경복궁")
                        .param("season", "SPRING").param("type", "ATTRACTION")
                        .param("categoryIds", "3", "5")
                        .param("mainCategoryId", "8")
                        .param("ktoSelectedPhotosJson", "[]")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        org.mockito.Mockito.verify(destinationSaveOrchestrationService, org.mockito.Mockito.never())
                .registerDestination(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        Element categorySection = document.selectFirst("[data-category-select]");
        assertThat(categorySection.select(".admin-field-error").text())
                .isEqualTo("대표 카테고리는 선택한 카테고리 중에서 지정해 주세요.");
        assertThat(document.selectFirst("[data-registration-error]").text())
                .contains("대표 카테고리는 선택한 카테고리 중에서 지정해 주세요.");
        // 대표 칸은 폼 안에 있어 다시 고른 값이 함께 저장된다.
        assertThat(categorySection.selectFirst("input[type=hidden][name=mainCategoryId][data-category-main]"))
                .isNotNull();
    }

    @Test
    void rejectedRegistrationShowsItsReasonAtTheTopAndKeepsInputsAndSelectedPhotos() throws Exception {
        String selection = "{\"qid\":\"Q12501\",\"photos\":[{\"fileName\":\"A.jpg\",\"main\":true},"
                + "{\"fileName\":\"B.jpg\",\"main\":false}]}";
        var document = Jsoup.parse(mockMvc.perform(multipart("/admin/destinations")
                        .param("wikidataQid", "Q12501")
                        .param("commonsSelectedPhotosJson", selection)
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].name", "만리장성")
                        .param("season", "SPRING").param("type", "ATTRACTION")
                        .param("ktoSelectedPhotosJson", "[]")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // 다시 그려진 폼은 맨 위부터 보이므로, 원인은 폼 앞쪽 안내에 둔다(버튼 옆에만 두지 않는다).
        Element alert = document.selectFirst("[data-registration-error]");
        assertThat(alert).isNotNull();
        assertThat(alert.closest("form")).isNull();
        assertThat(alert.text()).contains("여행지를 등록하지 못했습니다", "지역을 선택해 주세요.");
        assertThat(document.select(".admin-form-actions [role=alert]")).isEmpty();
        // 관리자 입력과 선택한 Commons 사진은 그대로 남는다.
        assertThat(document.selectFirst("[name=commonsSelectedPhotosJson]").val()).isEqualTo(selection);
        assertThat(document.selectFirst("[name=wikidataQid]").val()).isEqualTo("Q12501");
        assertThat(document.selectFirst("[name='translations[0].name']").val()).isEqualTo("만리장성");
    }
}
