package com.example.travlediary.controller.search;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.search.GlobalSearchService;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합검색 화면을 언어별로 그려 본다.
 *
 * <p>고정 문구(제목·탭·결과 수·빈 상태·페이지 이동)는 언어에 맞게 바뀌고,
 * 사용자가 쓴 커뮤니티 글 제목·본문은 그대로 남아야 한다.
 */
@WebMvcTest(GlobalSearchController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class GlobalSearchLocaleRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GlobalSearchService globalSearchService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    static Stream<Arguments> localizedScreens() {
        return Stream.of(
                Arguments.of(SupportedLanguage.KOREAN,
                        new String[]{"‘제주’ 검색 결과", "총 21개의 결과를 찾았습니다.",
                                "전체", "여행지", "커뮤니티", "여행코스", "여행정보", "이벤트", "공지사항",
                                "이전", "다음"}),
                Arguments.of(SupportedLanguage.ENGLISH,
                        new String[]{"Results for “제주”", "21 results found.",
                                "All", "Destinations", "Community", "Travel Courses", "Travel Info",
                                "Events", "Notices", "Previous", "Next"}),
                Arguments.of(SupportedLanguage.JAPANESE,
                        new String[]{"「제주」の検索結果", "全21件の結果が見つかりました。",
                                "すべて", "旅行スポット", "コミュニティ", "旅行コース", "旅行情報",
                                "イベント", "お知らせ", "前へ", "次へ"}),
                Arguments.of(SupportedLanguage.CHINESE_SIMPLIFIED,
                        new String[]{"“제주”的搜索结果", "共找到 21 条结果。",
                                "全部", "旅行地", "社区", "旅行路线", "旅行资讯", "活动", "公告",
                                "上一页", "下一页"}),
                Arguments.of(SupportedLanguage.CHINESE_TRADITIONAL,
                        new String[]{"「제주」的搜尋結果", "共找到 21 筆結果。",
                                "全部", "旅遊景點", "社群", "旅遊路線", "旅遊資訊", "活動", "公告",
                                "上一頁", "下一頁"}));
    }

    @ParameterizedTest
    @MethodSource("localizedScreens")
    void fixedSearchUiFollowsTheChosenLanguage(SupportedLanguage language, String[] expectedTexts)
            throws Exception {
        when(globalSearchService.search("제주", "all", 2, language)).thenReturn(resultPage());

        String body = renderSearch(language, "제주", "all", "2");

        assertThat(body).contains(expectedTexts);
        // 사용자가 쓴 글은 그대로다.
        assertThat(body).contains("제주 여행 질문", "일정이 궁금합니다.");
        // 없는 메시지 키가 남아 있지 않다.
        assertThat(body).doesNotContain("??");
        verify(globalSearchService).search("제주", "all", 2, language);
    }

    @ParameterizedTest
    @MethodSource("localizedScreens")
    void emptyStatesAndFiltersKeepQueryAndTypeInEveryLanguage(SupportedLanguage language,
                                                              String[] expectedTexts)
            throws Exception {
        when(globalSearchService.search("제주", "destination", 1, language))
                .thenReturn(new GlobalSearchPage("제주", "destination", List.of(), 0, 1, 10, 0, 1, 0));

        String body = renderSearch(language, "제주", "destination", "1");
        var document = Jsoup.parse(body);

        assertThat(document.select(".global-search-empty").text()).isNotBlank();
        assertThat(body).doesNotContain("??");
        // 탭 링크는 기존처럼 검색어와 type 을 그대로 들고 간다 (id/파라미터는 그대로)
        assertThat(document.select(".global-search-filters a[href*='type=destination']"))
                .isNotEmpty();
        assertThat(document.select(".global-search-filters a")).hasSize(7);
        assertThat(expectedTexts).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("localizedScreens")
    void theSearchFormPromptIsLocalizedWhenNothingWasSearched(SupportedLanguage language,
                                                              String[] expectedTexts)
            throws Exception {
        when(globalSearchService.search(null, "all", 1, language))
                .thenReturn(new GlobalSearchPage(null, "all", List.of(), 0, 1, 10, 0, 1, 0));

        String body = renderSearch(language, null, null, null);
        var document = Jsoup.parse(body);

        assertThat(document.select("#global-search-input").attr("placeholder")).isNotBlank();
        assertThat(document.select("label[for=global-search-input]").text()).isNotBlank();
        assertThat(document.select(".global-search-empty p").text()).isNotBlank();
        assertThat(body).doesNotContain("??");
        assertThat(expectedTexts).isNotEmpty();
    }

    private String renderSearch(SupportedLanguage language, String query, String type, String page)
            throws Exception {
        var request = get("/search")
                .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, language.getLanguageTag()));
        if (query != null) {
            request = request.param("q", query);
        }
        if (type != null) {
            request = request.param("type", type);
        }
        if (page != null) {
            request = request.param("page", page);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private GlobalSearchPage resultPage() {
        GlobalSearchResultDto post = new GlobalSearchResultDto();
        post.setType("community");
        post.setId(7L);
        post.setTitle("제주 여행 질문");
        post.setSummary("일정이 궁금합니다.");
        post.setCreatedAt(Timestamp.valueOf("2026-08-14 10:00:00"));
        post.setDetailUrl("/post/7");
        return new GlobalSearchPage("제주", "all", List.of(post), 21, 2, 10, 3, 1, 3);
    }
}
