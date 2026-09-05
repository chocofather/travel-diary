package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TravelInfoController.class)
@Import(SecurityConfig.class)
class TravelInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 여행정보 화면은 GENERAL 전용이고, 지역 범위를 안 고르면 국내부터 본다. */
    private static final TravelInfoScope DEFAULT_SCOPE = TravelInfoScope.DOMESTIC;
    private static final TravelInfoContentType DEFAULT_CONTENT_TYPE = TravelInfoContentType.GENERAL;
    private static final String DEFAULT_LIST_URL = "/travel-info?scope=DOMESTIC&contentType=GENERAL";

    @MockitoBean
    private TravelInfoService travelInfoService;
    @MockitoBean
    private FestivalDetailService festivalDetailService;
    @MockitoBean
    private InfoCategoryService infoCategoryService;
    @MockitoBean
    private ReferenceNameLocalizationService referenceNameLocalizationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenDefaultGeneralListWithPlaceholder() throws Exception {
        TravelInfoListItemDto general = item(11L, "해외여행 준비 체크리스트",
                TravelInfoScope.INTERNATIONAL, TravelInfoContentType.GENERAL, null);
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(general));
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(1L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/list"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("keyword", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("sort", "latest"))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("totalCount", 1L))
                .andExpect(model().attribute("pageTitle", "여행정보"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("등록된 이미지가 없습니다")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.selectFirst(
                            "button[data-filter-name=categoryId][data-filter-value='']"))
                            .isNotNull()
                            .extracting(element -> element.attr("aria-pressed"))
                            .isEqualTo("true");
                    assertThat(document.selectFirst("a[data-travel-info-sort].is-active"))
                            .isNotNull()
                            .extracting(element -> element.attr("data-sort-value"))
                            .isEqualTo("latest");
                    assertThat(document.selectFirst("a.travel-info-card-link"))
                            .isNotNull()
                            .extracting(element -> element.attr("href"))
                            .asString()
                            .startsWith("/travel-info/11?returnUrl=");
                });

        verify(infoCategoryService).getVisibleByContentType(TravelInfoContentType.GENERAL);
    }

    @Test
    void listLinksGeneralAndFestivalCardsToTheirDedicatedDetails() throws Exception {
        TravelInfoListItemDto general = item(11L, "일반 여행정보",
                TravelInfoScope.DOMESTIC, TravelInfoContentType.GENERAL, null);
        TravelInfoListItemDto festival = item(12L, "가을 축제",
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, null);
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(general, festival));
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(2L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    var linksByTitle = document.select("a.travel-info-card-link").stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    element -> element.text(),
                                    element -> element.attr("href")));
                    assertThat(linksByTitle.get("일반 여행정보"))
                            .startsWith("/travel-info/11?returnUrl=");
                    assertThat(linksByTitle.get("가을 축제"))
                            .startsWith("/festivals/12?returnUrl=");
                    assertThat(document.select(".travel-info-card:not(.is-festival) "
                            + ".travel-info-thumbnail"))
                            .singleElement();
                    assertThat(document.select(".travel-info-card:not(.is-festival) "
                            + ".travel-info-type-meta"))
                            .singleElement()
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("국내 · 일반");
                    assertThat(document.select(".travel-info-card.is-festival "
                            + ".travel-info-festival-thumbnail "
                            + ".travel-info-thumbnail-placeholder"))
                            .singleElement();
                });
    }

    @Test
    void loggedInUserIdIsPassedToListAndDetailBookmarkPopulation() throws Exception {
        TravelInfoListItemDto item = item(10L, "저장할 여행정보", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.GENERAL, null);
        item.setBookmarked(true);
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        detail.setBookmarked(true);
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(item));
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(1L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category()));
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);
        CustomUserDetails principal = principal(7L);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        mockMvc.perform(get("/travel-info").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-bookmarked=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-label=\"여행정보 저장 취소\"")));
        mockMvc.perform(get("/travel-info/10").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("저장됨")));

        verify(travelInfoService).populatePublicListBookmarks(List.of(item), 7L);
        verify(travelInfoService).populatePublicDetailBookmark(detail, 7L);
    }

    @Test
    void filtersAndPaginationAreNormalizedAndPassedToTheModel() throws Exception {
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                List.of(3L), null, "latest", 48L, 48))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                List.of(3L), null))
                .thenReturn(60L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL))
                .thenReturn(List.of(category(3L, "축제·행사", 1, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get("/travel-info")
                        .param("scope", "domestic")
                        .param("contentType", "festival")
                        .param("categoryId", "3")
                        .param("page", "2")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attribute("contentType", TravelInfoContentType.FESTIVAL))
                .andExpect(model().attribute("categoryIds", List.of(3L)))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("pageSize", 48))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("정보 유형"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제·행사 분류")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "조건에 맞는 여행정보가 없습니다.")));

        verify(travelInfoService).getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                List.of(3L), null, "latest", 48L, 48);
    }

    @Test
    void repeatedCategoriesAreDeduplicatedAndCombinedWithScopeAndHiddenContentType() throws Exception {
        List<Long> selectedCategoryIds = List.of(1L, 3L, 5L);
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds, null, "latest", 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds, null)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL)).thenReturn(List.of(
                category(1L, "축제·행사", 1, TravelInfoContentType.FESTIVAL),
                category(3L, "음악", 2, TravelInfoContentType.FESTIVAL),
                category(5L, "지역행사", 3, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get("/travel-info")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "FESTIVAL")
                        .param("categoryId", "1", "1", "3", "invalid", "-2", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categoryIds", selectedCategoryIds))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            "button[data-filter-name=categoryId].is-active").eachAttr("data-filter-value"))
                            .containsExactly("1", "3", "5");
                    assertThat(document.selectFirst(
                            "button[data-filter-name=categoryId][data-filter-value='']"))
                            .isNotNull()
                            .extracting(element -> element.attr("aria-pressed"))
                            .isEqualTo("false");
                });

        verify(travelInfoService).getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds, null, "latest", 0L, 12);
        verify(travelInfoService).countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds, null);
    }

    @Test
    void directFestivalUrlKeepsBackendFilterAndUsesPosterFocusedFestivalCard() throws Exception {
        TravelInfoListItemDto festival = item(10L, "여름 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, "/uploads/travel-info/festivals/poster.jpg");
        festival.setStartDate(LocalDate.parse("2026-08-01"));
        festival.setEndDate(LocalDate.parse("2026-08-03"));
        when(travelInfoService.getPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(festival));
        when(travelInfoService.countPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null)).thenReturn(1L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL))
                .thenReturn(List.of(category(3L, "축제·행사", 1, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get("/travel-info").param("contentType", "FESTIVAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/list"))
                .andExpect(model().attribute("contentType", TravelInfoContentType.FESTIVAL))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("정보 유형"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여름 축제")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    // 축제·행사 화면은 지역 범위 [전체][국내][해외]를 그대로 쓰고, 기본은 전체다
                    assertThat(document.select(
                            "a[data-filter-name=primary][data-filter-content-type=FESTIVAL]")
                            .eachText()).containsExactly("전체", "국내", "해외");
                    assertThat(document.selectFirst(
                            "a[data-filter-content-type=FESTIVAL][data-filter-value=''].is-active"))
                            .isNotNull();
                    // 일반 여행정보 버튼은 이 화면에 없다
                    assertThat(document.select("a[data-filter-content-type=GENERAL]")).isEmpty();
                    assertThat(document.selectFirst(
                            "button[data-filter-name=categoryId][data-filter-value='3']"))
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("축제·행사");
                    assertThat(document.select(".travel-info-card.is-festival"))
                            .singleElement();
                    assertThat(document.select(".travel-info-card.is-festival "
                            + ".travel-info-festival-thumbnail"))
                            .singleElement();
                    assertThat(document.selectFirst(".travel-info-card.is-festival "
                            + ".travel-info-festival-thumbnail img"))
                            .extracting(element -> element.attr("src"))
                            .isEqualTo("/uploads/travel-info/festivals/poster.jpg");
                    assertThat(document.select(".travel-info-card.is-festival "
                            + ".travel-info-festival-period"))
                            .singleElement()
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("2026.08.01 ~ 2026.08.03");
                    assertThat(document.select(".travel-info-card.is-festival "
                            + ".travel-info-type-meta"))
                            .singleElement()
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("국내");
                    assertThat(document.select(".travel-info-card.is-festival "
                            + ".travel-info-period"))
                            .isEmpty();
                });

        verify(travelInfoService).getPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null, "latest", 0L, 12);
        verify(infoCategoryService).getVisibleByContentType(TravelInfoContentType.FESTIVAL);
    }

    @Test
    void ajaxRequestReturnsFilteredPaginatedResultsAndFestivalCategoryFilter() throws Exception {
        TravelInfoListItemDto festival = item(10L, "국내 여름 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, null);
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                List.of(3L, 5L), "축제", "views", 12L, 12))
                .thenReturn(List.of(festival));
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                List.of(3L, 5L), "축제"))
                .thenReturn(25L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL)).thenReturn(List.of(
                category(3L, "축제·행사", 1, TravelInfoContentType.FESTIVAL),
                category(5L, "음악", 2, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get(URI.create("/travel-info?keyword=%EC%B6%95%EC%A0%9C"
                        + "&scope=DOMESTIC&contentType=FESTIVAL"
                        + "&categoryId=3&categoryId=5&sort=views&page=2"))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/fragments/list-async :: response"))
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attribute("contentType", TravelInfoContentType.FESTIVAL))
                .andExpect(model().attribute("categoryIds", List.of(3L, 5L)))
                .andExpect(model().attribute("keyword", "축제"))
                .andExpect(model().attribute("sort", "views"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 3))
                .andExpect(model().attribute("listUrl",
                        "/travel-info?keyword=%EC%B6%95%EC%A0%9C"
                                + "&scope=DOMESTIC&contentType=FESTIVAL"
                                + "&categoryId=3&categoryId=5&sort=views&page=2"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"travel-info-results\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"travel-info-category-filter-template\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"travel-info-category-filter\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제·행사 분류")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제·행사")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("국내 여름 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "contentType=FESTIVAL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "keyword=%EC%B6%95%EC%A0%9C")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "categoryId=3&amp;categoryId=5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "sort=views")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-info-heading"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-info-filters"))));

        verify(infoCategoryService).getVisibleByContentType(TravelInfoContentType.FESTIVAL);
    }

    @Test
    void ajaxRequestWithoutContentTypeReturnsGeneralCategoryFilter() throws Exception {
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, null, List.of(), null))
                .thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category(1L, "계절여행", 1, TravelInfoContentType.GENERAL)));

        mockMvc.perform(get("/travel-info")
                        .param("scope", "DOMESTIC")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"travel-info-category-filter-template\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계절여행")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("축제·행사 분류"))));

        verify(infoCategoryService).getVisibleByContentType(TravelInfoContentType.GENERAL);
    }

    /**
     * 여행정보와 축제·행사는 각각 독립된 화면이다.
     * 국내/해외 메뉴로 들어오면 그 지역이 잡히고, 축제 글이 섞이지 않는다.
     */
    @Test
    void generalScreenShowsOnlyDomesticOrInternationalAndNeverMixesFestivals() throws Exception {
        for (TravelInfoScope scope : List.of(
                TravelInfoScope.DOMESTIC, TravelInfoScope.INTERNATIONAL)) {
            when(travelInfoService.getPublicList(
                    scope, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                    .thenReturn(List.of());
            when(travelInfoService.countPublicList(
                    scope, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(0L);
            when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                    .thenReturn(List.of(category()));

            mockMvc.perform(get("/travel-info").param("scope", scope.name()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("scope", scope))
                    // 화면 유형은 언제나 GENERAL 이라 축제 글이 섞이지 않는다
                    .andExpect(model().attribute("contentType", DEFAULT_CONTENT_TYPE))
                    .andExpect(model().attribute("pageTitle", "여행정보"))
                    .andExpect(result -> {
                        var document = Jsoup.parse(result.getResponse().getContentAsString());
                        // 일반 여행정보에는 국내/해외만 있고 '전체'도 축제·행사도 없다
                        assertThat(document.select(
                                "a[data-filter-name=primary][data-filter-content-type=GENERAL]")
                                .eachText()).containsExactly("국내", "해외");
                        assertThat(document.select("a[data-filter-content-type=FESTIVAL]"))
                                .isEmpty();
                        assertThat(document.selectFirst(
                                "a[data-filter-value=" + scope.name() + "].is-active"))
                                .isNotNull();
                    });

            // 반대쪽 지역으로 넘어가는 링크가 같은 화면 안에 있다
            TravelInfoScope other = scope == TravelInfoScope.DOMESTIC
                    ? TravelInfoScope.INTERNATIONAL : TravelInfoScope.DOMESTIC;
            mockMvc.perform(get("/travel-info").param("scope", scope.name()))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "scope=" + other.name() + "&amp;contentType=GENERAL")));

            verify(travelInfoService, org.mockito.Mockito.atLeastOnce()).getPublicList(
                    scope, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12);
        }
    }

    @Test
    void blankLatestAndInvalidSortValuesUseCanonicalLatestOrder() throws Exception {
        when(travelInfoService.getPublicList(
                DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL)).thenReturn(List.of());

        for (String sort : List.of("", "latest", "abc")) {
            mockMvc.perform(get("/travel-info").param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("sort", "latest"))
                    .andExpect(model().attribute("listUrl", DEFAULT_LIST_URL));
        }

        verify(travelInfoService, org.mockito.Mockito.times(3)).getPublicList(
                DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12);
    }

    @Test
    void titleKeywordIsNormalizedCombinedWithFiltersAndRenderedSafely() throws Exception {
        when(travelInfoService.getPublicList(
                TravelInfoScope.INTERNATIONAL, DEFAULT_CONTENT_TYPE, List.of(2L, 5L), "파리", "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.INTERNATIONAL, DEFAULT_CONTENT_TYPE, List.of(2L, 5L), "파리"))
                .thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info")
                        .param("keyword", "  파리  ")
                        .param("scope", "INTERNATIONAL")
                        .param("categoryId", "2", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "파리"))
                .andExpect(model().attribute("listUrl",
                        "/travel-info?keyword=%ED%8C%8C%EB%A6%AC"
                                + "&scope=INTERNATIONAL&contentType=GENERAL"
                                + "&categoryId=2&categoryId=5"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"파리\"")))
                // 빈 결과 문구는 messages 로 옮겼고, 검색어는 파라미터로 들어간다.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "‘파리’에 해당하는 여행정보를 찾지 못했습니다.")));

        verify(travelInfoService).getPublicList(
                TravelInfoScope.INTERNATIONAL, DEFAULT_CONTENT_TYPE, List.of(2L, 5L), "파리", "latest", 0L, 12);
        verify(travelInfoService).countPublicList(
                TravelInfoScope.INTERNATIONAL, DEFAULT_CONTENT_TYPE, List.of(2L, 5L), "파리");
    }

    @Test
    void blankKeywordIsIgnoredAndLongKeywordIsSafelyLimited() throws Exception {
        String limitedKeyword = "가".repeat(100);
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(0L);
        when(travelInfoService.getPublicList(
                DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), limitedKeyword, "latest", 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(
                DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), limitedKeyword)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL)).thenReturn(List.of());

        mockMvc.perform(get("/travel-info").param("keyword", "   \t"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/travel-info").param("keyword", "가".repeat(101)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", limitedKeyword));

        verify(travelInfoService).getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12);
        verify(travelInfoService).getPublicList(
                DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), limitedKeyword, "latest", 0L, 12);
    }

    @Test
    void invalidFiltersAndNonPositivePageSizeFallBackSafely() throws Exception {
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL)).thenReturn(List.of());

        mockMvc.perform(get("/travel-info")
                        .param("scope", "unknown")
                        .param("contentType", "unsupported")
                        .param("categoryId", "not-a-number", "-3", "0")
                        .param("page", "-2")
                        .param("size", "0"))
                .andExpect(status().isOk())
                // 알 수 없는 값은 무시하고 기본 화면(국내 여행정보)으로 되돌린다
                .andExpect(model().attribute("scope", DEFAULT_SCOPE))
                .andExpect(model().attribute("contentType", DEFAULT_CONTENT_TYPE))
                .andExpect(model().attribute("categoryIds", List.of()))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("totalPages", 0));
    }

    @Test
    void unknownPositiveCategoryIdIsHandledAsAnEmptyPublicFilterResult() throws Exception {
        when(travelInfoService.getPublicList(null, null, List.of(999L), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(999L), null)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info").param("categoryId", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categoryIds", List.of(999L)))
                .andExpect(model().attribute("totalCount", 0L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "조건에 맞는 여행정보가 없습니다.")));
    }

    @Test
    void legacyTravelInfoFestivalDetailRedirectsToDedicatedFestivalUrl() throws Exception {
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(true);

        mockMvc.perform(get("/travel-info/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/festivals/10"));

        verify(travelInfoService, never()).getPublicDetail(10L);
    }

    @Test
    void legacyFestivalRedirectPreservesOnlyValidatedTravelInfoReturnUrl() throws Exception {
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(true);

        mockMvc.perform(get("/travel-info/10")
                        .param("returnUrl", "/travel-info?contentType=FESTIVAL&page=2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/festivals/10?returnUrl=%2Ftravel-info%3FcontentType%3DFESTIVAL%26page%3D2"));

        mockMvc.perform(get("/travel-info/10")
                        .param("returnUrl", "https://evil.example/travel-info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/festivals/10?returnUrl=%2Ftravel-info"));
    }

    @Test
    void guestCanOpenFestivalDetailWithStructuredDataLocalImageAndType3Attribution() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        detail.setContent("<p><span class=\"ql-font-noto-serif-kr\">축제 본문</span></p>"
                + "<img src=\"/uploads/editor/festival.png\" width=\"600\" alt=\"축제\">");
        detail.setPeriods(List.of(
                new TravelInfoPeriodDto(LocalDate.parse("2026-09-02"), LocalDate.parse("2026-10-24"))));
        FestivalInfo festivalInfo = festivalInfo();
        InfoImage mainImage = mainImage("KOGL_TYPE_3");
        FestivalDetailDto festival = new FestivalDetailDto(detail, festivalInfo, mainImage);
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(festival);

        mockMvc.perform(get(URI.create(
                "/festivals/10?returnUrl=%2Ftravel-info%3Fkeyword%3D"
                        + "%25ED%258C%258C%25EB%25A6%25AC%26scope%3Ddomestic"
                        + "%26contentType%3Dfestival%26categoryId%3D1%26categoryId%3D3"
                        + "%26categoryId%3D1%26sort%3Dviews%26page%3D2%26size%3D24")))
                .andExpect(status().isOk())
                .andExpect(view().name("festivals/detail"))
                .andExpect(model().attribute("festival", festival))
                .andExpect(model().attribute("pageTitle", "공개 여행정보 | 축제·행사"))
                .andExpect(model().attribute("listUrl",
                        "/travel-info?keyword=%ED%8C%8C%EB%A6%AC"
                                + "&scope=DOMESTIC&contentType=FESTIVAL"
                                + "&categoryId=1&categoryId=3&sort=views&page=2&size=24"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공개 여행정보")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026.09.02")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026.10.24")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("경복궁")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서울특별시 종로구 사직로 161")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1부 18:20~20:10")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1인 60,000원")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("국가유산청 궁능유적본부")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("국가유산진흥원")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1522-2295")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공식 홈페이지")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("target=\"_blank\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rel=\"noopener noreferrer\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/uploads/travel-info/festivals/local.jpg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("https://tong.visitkorea.or.kr/source.jpg"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("한국관광공사")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공공누리 제3유형")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"festival-detail-content rich-text-content\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<span class=\"ql-font-noto-serif-kr\">축제 본문</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("행사 정보")));
    }

    @Test
    void festivalDetailGroupsRegistrationAndViewsWithTitleAndKeepsFooterSecondary() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(
                new FestivalDetailDto(detail, festivalInfo(), mainImage("KOGL_TYPE_3")));

        mockMvc.perform(get("/festivals/10"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".festival-detail-heading > .festival-detail-meta"))
                            .singleElement()
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("등록 2026.08.01 · 조회 18");
                    assertThat(document.select(".festival-detail-footer .festival-detail-meta"))
                            .isEmpty();
                    assertThat(document.select(".festival-detail-title-row"
                            + " .festival-detail-bookmark"))
                            .singleElement();
                    assertThat(document.select(".festival-detail-bookmark"
                            + " .travel-info-bookmark-label"))
                            .singleElement();
                    assertThat(document.select(".festival-detail-footer .festival-detail-back"))
                            .singleElement();
                });
    }

    @Test
    void festivalDetailWithoutImageOrOptionalValuesRendersWithoutEmptyRows() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        detail.setPeriods(List.of());
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(10L);
        festivalInfo.setEventPlace("   ");
        festivalInfo.setAddress(null);
        festivalInfo.setSponsor1Tel("02-1111-2222");
        festivalInfo.setHomepageUrl("javascript:alert(1)");
        when(festivalDetailService.getPublicDetail(10L))
                .thenReturn(new FestivalDetailDto(detail, festivalInfo, (InfoImage) null));

        mockMvc.perform(get("/festivals/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("대표이미지가 없습니다")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("사진 출처:"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">장소<"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">주소<"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">주최<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("02-1111-2222")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("javascript:alert"))));
    }

    @Test
    void festivalDetailDisplaysType1AttributionFromStoredLicense() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(
                new FestivalDetailDto(detail, festivalInfo(), mainImage("KOGL_TYPE_1")));

        mockMvc.perform(get("/festivals/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공공누리 제1유형")));
    }

    @Test
    void singleFestivalImageHidesGalleryNavigationAndDuplicateFestivalKicker() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        detail.setCategoryName("축제");
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(
                new FestivalDetailDto(detail, festivalInfo(), mainImage("KOGL_TYPE_1")));

        mockMvc.perform(get("/festivals/10"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".festival-detail-category")).singleElement()
                            .extracting(org.jsoup.nodes.Element::text)
                            .isEqualTo("축제");
                    assertThat(document.select(".festival-detail-kicker")).isEmpty();
                    assertThat(document.select("[data-festival-gallery-slide]")).hasSize(1);
                    assertThat(document.select("[data-festival-gallery-prev],"
                            + "[data-festival-gallery-next],.festival-detail-gallery-counter"))
                            .isEmpty();
                });
    }

    @Test
    void multipleFestivalImagesRenderInOrderWithPerImageAttributionAndGalleryControls() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        InfoImage main = mainImage("KOGL_TYPE_1");
        main.setOrderIndex(1);
        InfoImage additional = new InfoImage();
        additional.setImageUrl("/uploads/travel-info/festivals/additional.jpg");
        additional.setSourceType("KTO_TOURAPI");
        additional.setSourceName("한국관광공사");
        additional.setSourceTitle("야간 공연");
        additional.setLicenseType("KOGL_TYPE_3");
        additional.setSourceImageUrl("https://tong.visitkorea.or.kr/cms/resource/35/source.jpg");
        additional.setIsMain(false);
        additional.setOrderIndex(2);
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(
                new FestivalDetailDto(detail, festivalInfo(), List.of(main, additional)));

        mockMvc.perform(get("/festivals/10"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("[data-festival-gallery-slide] img").eachAttr("src"))
                            .containsExactly(
                                    "/uploads/travel-info/festivals/local.jpg",
                                    "/uploads/travel-info/festivals/additional.jpg");
                    assertThat(document.select("[data-festival-gallery-prev]")).hasSize(1);
                    assertThat(document.select("[data-festival-gallery-next]")).hasSize(1);
                    assertThat(document.selectFirst(".festival-detail-gallery-counter").text())
                            .isEqualTo("1 / 2");
                    assertThat(document.select("[data-festival-gallery-slide]").get(0)
                            .attr("data-license-label")).isEqualTo("공공누리 제1유형");
                    assertThat(document.select("[data-festival-gallery-slide]").get(1)
                            .attr("data-license-label")).isEqualTo("공공누리 제3유형");
                    assertThat(result.getResponse().getContentAsString())
                            .doesNotContain("https://tong.visitkorea.or.kr/cms/resource/35/source.jpg");
                });
    }

    @Test
    void generalDetailWithoutMeaningfulUpdateHidesPeriodAndUpdatedMeta() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        detail.setUpdatedAt(Timestamp.valueOf("2026-08-01 18:30:00"));
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/travel-info/10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("listUrl", "/travel-info"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("행사 기간"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("최종 수정"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/travel-info\"")));
    }

    @Test
    void latestSortReturnUrlIsAcceptedAndCanonicalizedWithoutSortParameter() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/travel-info/10")
                        .param("returnUrl", "/travel-info?sort=latest&page=2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("listUrl", "/travel-info?page=2"));
    }

    @Test
    void unsafeOrUnknownReturnUrlsFallBackToTravelInfoList() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);
        List<String> unsafeUrls = List.of(
                "https://evil.example/travel-info",
                "//evil.example/travel-info",
                "javascript:alert(1)",
                "/post/1",
                "/travel-info?unknown=value",
                "/travel-info?keyword=one&keyword=two",
                "/travel-info?scope=DOMESTIC&scope=INTERNATIONAL",
                "/travel-info?sort=popular",
                "/travel-info?sort=latest&sort=views",
                "/travel-info?categoryId=-1");

        for (String unsafeUrl : unsafeUrls) {
            mockMvc.perform(get("/travel-info/10").param("returnUrl", unsafeUrl))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("listUrl", "/travel-info"));
        }
    }

    @Test
    void missingOrHiddenPublicDetailReturnsNotFound() throws Exception {
        when(travelInfoService.getPublicDetail(999L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "여행정보를 찾을 수 없습니다."));

        mockMvc.perform(get(URI.create("/travel-info/999?returnUrl=%2Ftravel-info")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyListAndNumericDetailGetsArePublicAndAdminPolicyStillRejectsRegularUsers() throws Exception {
        when(travelInfoService.getPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(DEFAULT_SCOPE, DEFAULT_CONTENT_TYPE, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL)).thenReturn(List.of());
        when(travelInfoService.getPublicDetail(10L))
                .thenReturn(detail(TravelInfoContentType.GENERAL));

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/travel-info"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/travel-info/10"))
                .andExpect(status().isOk());
        mockMvc.perform(get(URI.create(
                        "/travel-info/10?returnUrl=%2Ftravel-info%3Fscope%3DDOMESTIC")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/travel-info/10?returnUrl=%2Ftravel-info"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(put("/travel-info/10"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(patch("/travel-info/10"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(delete("/travel-info/10"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/travel-info/abc?returnUrl=%2Ftravel-info"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/travel-info").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private TravelInfoListItemDto item(Long id, String title, TravelInfoScope scope,
                                       TravelInfoContentType contentType, String thumbnailUrl) {
        TravelInfoListItemDto item = new TravelInfoListItemDto();
        item.setId(id);
        item.setTitle(title);
        item.setScope(scope);
        item.setContentType(contentType);
        item.setCategoryId(3L);
        item.setCategoryName("계절여행");
        item.setThumbnailUrl(thumbnailUrl);
        item.setViews(17);
        item.setCreatedAt(Timestamp.valueOf("2026-04-01 10:00:00"));
        return item;
    }

    private InfoCategory category() {
        return category(3L, "계절여행", 1, TravelInfoContentType.GENERAL);
    }

    private InfoCategory category(Long id, String name, int displayOrder) {
        return category(id, name, displayOrder, TravelInfoContentType.GENERAL);
    }

    private InfoCategory category(Long id,
                                  String name,
                                  int displayOrder,
                                  TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setContentType(contentType);
        category.setDisplayOrder(displayOrder);
        category.setIsVisible(true);
        return category;
    }

    private TravelInfoDetailDto detail(TravelInfoContentType contentType) {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("공개 여행정보");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(contentType);
        detail.setCategoryName("계절여행");
        detail.setContent("<p>본문</p>");
        detail.setViews(18);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-01 10:00:00"));
        detail.setUpdatedAt(Timestamp.valueOf("2026-08-02 11:00:00"));
        return detail;
    }

    private FestivalInfo festivalInfo() {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(10L);
        info.setEventPlace("경복궁");
        info.setAddress("서울특별시 종로구 사직로 161");
        info.setPlayTime("1부 18:20~20:10 / 2부 19:30~21:20");
        info.setUseTime("1인 60,000원");
        info.setSponsor1("국가유산청 궁능유적본부");
        info.setSponsor1Tel("02-1234-5678");
        info.setSponsor2("국가유산진흥원");
        info.setSponsor2Tel("02-9876-5432");
        info.setContactTel("1522-2295");
        info.setHomepageUrl("https://www.example.com/festival");
        info.setSourceType("KTO_TOURAPI");
        info.setExternalContentId("2648460");
        return info;
    }

    private InfoImage mainImage(String licenseType) {
        InfoImage image = new InfoImage();
        image.setImageUrl("/uploads/travel-info/festivals/local.jpg");
        image.setSourceType("KTO_TOURAPI");
        image.setSourceName("한국관광공사");
        image.setSourceTitle("경복궁 별빛야행");
        image.setLicenseType(licenseType);
        image.setSourceImageUrl("https://tong.visitkorea.or.kr/source.jpg");
        image.setIsMain(true);
        image.setOrderIndex(1);
        image.setInfoId(10L);
        return image;
    }

    private CustomUserDetails principal(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("traveler");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }
}
