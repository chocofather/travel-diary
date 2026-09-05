package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.TravelInfoListItemDto;
import jakarta.servlet.http.Cookie;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 목록 화면이 언어별로 실제 문구를 그려 내는지 본다.
 *
 * <p>카테고리 이름은 DB 번역 결과를 그대로 쓰고 messages 로 바뀌지 않아야 한다.
 */
@WebMvcTest(TravelInfoController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class TravelInfoListLocaleRenderingTest {

    @MockitoBean private TravelInfoService travelInfoService;
    @MockitoBean private FestivalDetailService festivalDetailService;
    @MockitoBean private InfoCategoryService infoCategoryService;
    @MockitoBean private ReferenceNameLocalizationService referenceNameLocalizationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "ko, 여행정보, 국내, 최신순, 저장, 이전",
            "en, Travel Info, Korea, Newest, Save, Previous",
            "ja, 旅行情報, 韓国国内, 新着順, 保存, 前へ",
            "zh-CN, 旅游资讯, 韩国国内, 最新, 收藏, 上一页",
            "zh-TW, 旅遊資訊, 韓國國內, 最新, 收藏, 上一頁"
    })
    void theGeneralListRendersItsLabelsInTheRequestedLanguage(
            String tag, String title, String domestic, String latest,
            String save, String previous) throws Exception {
        givenGeneralList();

        Document document = render("/travel-info", tag);

        assertThat(document.select("#travel-info-title").text()).isEqualTo(title);
        assertThat(document.select("[data-filter-value=DOMESTIC]").text()).isEqualTo(domestic);
        assertThat(document.select("[data-sort-value=latest]").text()).isEqualTo(latest);
        assertThat(document.select(".travel-info-bookmark-label").text()).isEqualTo(save);
        assertThat(document.select(".travel-info-page-direction").first().text())
                .isEqualTo(previous);
        // 카테고리 이름은 DB 번역 값 그대로다.
        assertThat(document.select(".travel-info-category").text()).isEqualTo("Seasonal travel");
    }

    @ParameterizedTest
    @CsvSource({
            "ko, 축제·행사, 전체, 축제·행사 분류",
            "en, Festivals & Events, All, Event type",
            "ja, 祭り・イベント, すべて, イベント分類",
            "zh-CN, 庆典·活动, 全部, 活动分类",
            "zh-TW, 慶典·活動, 全部, 活動分類"
    })
    void theFestivalListRendersItsOwnLabelsInTheRequestedLanguage(
            String tag, String title, String all, String categoryLabel) throws Exception {
        givenFestivalList();

        Document document = render("/travel-info?contentType=FESTIVAL", tag);

        assertThat(document.select("#travel-info-title").text()).isEqualTo(title);
        // 축제 화면만 지역 범위에 '전체'가 있다.
        assertThat(document.select(".travel-info-filter-pill[data-filter-content-type=FESTIVAL]")
                .first().text()).isEqualTo(all);
        assertThat(document.select("#travel-info-category-filter .travel-info-filter-label").text())
                .isEqualTo(categoryLabel);
    }

    @ParameterizedTest
    @CsvSource({"ko, 총 30개의 여행정보", "en, 30 results", "ja, 全30件",
            "zh-CN, 共 30 条", "zh-TW, 共 30 筆"})
    void theResultCountUsesAMessageParameter(String tag, String expected) throws Exception {
        givenGeneralList();

        Document document = render("/travel-info", tag);

        assertThat(document.select(".travel-info-results-header p").text()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "ko, 조건에 맞는 여행정보가 없습니다.",
            "en, No travel info matches these filters.",
            "ja, 条件に合う旅行情報がありません。",
            "zh-CN, 没有符合条件的旅游资讯。",
            "zh-TW, 沒有符合條件的旅遊資訊。"
    })
    void theEmptyResultNoticeIsTranslated(String tag, String expected) throws Exception {
        when(infoCategoryService.getVisibleByContentType(any())).thenReturn(List.of());
        when(travelInfoService.getPublicList(any(), any(), any(), any(), any(), anyOffset(), anySize()))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(any(), any(), any(), any())).thenReturn(0L);

        Document document = render("/travel-info", tag);

        assertThat(document.select(".travel-info-empty p").text()).isEqualTo(expected);
    }

    @Test
    void theBookmarkButtonCarriesTranslatedWordingForTheScript() throws Exception {
        givenGeneralList();

        Document document = render("/travel-info", "en");
        var button = document.select("[data-travel-info-bookmark]").first();

        assertThat(button.attr("aria-label")).isEqualTo("Save this travel info");
        assertThat(button.attr("data-label-save")).isEqualTo("Save");
        assertThat(button.attr("data-label-saved")).isEqualTo("Saved");
        assertThat(button.attr("data-aria-remove")).isEqualTo("Remove from saved");
        assertThat(button.attr("data-failed-message"))
                .isEqualTo("We could not update your saved items.");
        assertThat(document.select("#travel-info-async-message").attr("data-load-failed-message"))
                .isEqualTo("We could not load the list. Reloading the page.");
    }

    private long anyOffset() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private int anySize() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private void givenGeneralList() {
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category(3L, "계절여행", TravelInfoContentType.GENERAL)));
        when(referenceNameLocalizationService.localizeInfoCategories(any(), any()))
                .thenReturn(Map.of(3L, "Seasonal travel"));
        when(travelInfoService.getPublicList(any(), any(), any(), any(), any(), anyOffset(), anySize()))
                .thenReturn(List.of(listItem(TravelInfoContentType.GENERAL)));
        // 페이지 이동 문구까지 그려지도록 여러 쪽 분량으로 둔다.
        when(travelInfoService.countPublicList(any(), any(), any(), any())).thenReturn(30L);
    }

    private void givenFestivalList() {
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL))
                .thenReturn(List.of(category(4L, "문화축제", TravelInfoContentType.FESTIVAL)));
        when(referenceNameLocalizationService.localizeInfoCategories(any(), any()))
                .thenReturn(Map.of(4L, "Culture festival"));
        when(travelInfoService.getPublicList(any(), any(), any(), any(), any(), anyOffset(), anySize()))
                .thenReturn(List.of(listItem(TravelInfoContentType.FESTIVAL)));
        // 페이지 이동 문구까지 그려지도록 여러 쪽 분량으로 둔다.
        when(travelInfoService.countPublicList(any(), any(), any(), any())).thenReturn(30L);
    }

    /** 실제 화면과 같은 방식으로 언어를 고른다 (언어 선택 쿠키). */
    private Document render(String path, String languageTag) throws Exception {
        String html = mockMvc.perform(get(path)
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Jsoup.parse(html);
    }

    private InfoCategory category(Long id, String name, TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setContentType(contentType);
        category.setDisplayOrder(1);
        category.setIsVisible(true);
        return category;
    }

    private TravelInfoListItemDto listItem(TravelInfoContentType contentType) {
        TravelInfoListItemDto item = new TravelInfoListItemDto();
        item.setId(10L);
        item.setTitle("Spring packing list");
        item.setScope(TravelInfoScope.DOMESTIC);
        item.setContentType(contentType);
        item.setCategoryId(contentType == TravelInfoContentType.FESTIVAL ? 4L : 3L);
        item.setCategoryName("Seasonal travel");
        item.setViews(7);
        item.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return item;
    }
}
