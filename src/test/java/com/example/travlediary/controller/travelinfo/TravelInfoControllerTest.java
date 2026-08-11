package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TravelInfoController.class)
@Import(SecurityConfig.class)
class TravelInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelInfoService travelInfoService;
    @MockitoBean
    private InfoCategoryService infoCategoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenDefaultListWithThumbnailFestivalPeriodAndPlaceholder() throws Exception {
        TravelInfoListItemDto festival = item(10L, "서울 봄 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, "/uploads/travel-info/thumbnails/festival.jpg");
        festival.setStartDate(LocalDate.parse("2026-04-01"));
        festival.setEndDate(LocalDate.parse("2026-04-03"));
        TravelInfoListItemDto general = item(11L, "해외여행 준비 체크리스트",
                TravelInfoScope.INTERNATIONAL, TravelInfoContentType.GENERAL, null);
        when(travelInfoService.getPublicList(null, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(festival, general));
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(2L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/list"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("keyword", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("sort", "latest"))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("totalCount", 2L))
                .andExpect(model().attribute("pageTitle", "여행정보"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서울 봄 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/uploads/travel-info/thumbnails/festival.jpg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-03")))
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
                            .startsWith("/travel-info/10?returnUrl=");
                });
    }

    @Test
    void loggedInUserIdIsPassedToListAndDetailBookmarkPopulation() throws Exception {
        TravelInfoListItemDto item = item(10L, "저장할 여행정보", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.GENERAL, null);
        item.setBookmarked(true);
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        detail.setBookmarked(true);
        when(travelInfoService.getPublicList(null, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(item));
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(1L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));
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
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">주제</span>")))
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
        when(infoCategoryService.getVisible()).thenReturn(List.of(
                category(1L, "계절여행", 1),
                category(3L, "여행추천", 2),
                category(5L, "교통", 3)));

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
    void directFestivalUrlKeepsBackendFilterAndFestivalCardPresentation() throws Exception {
        TravelInfoListItemDto festival = item(10L, "여름 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, null);
        festival.setStartDate(LocalDate.parse("2026-08-01"));
        festival.setEndDate(LocalDate.parse("2026-08-03"));
        when(travelInfoService.getPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of(festival));
        when(travelInfoService.countPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null)).thenReturn(1L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info").param("contentType", "FESTIVAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/list"))
                .andExpect(model().attribute("contentType", TravelInfoContentType.FESTIVAL))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("정보 유형"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여름 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제 기간")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-08-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-08-03")));

        verify(travelInfoService).getPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), null, "latest", 0L, 12);
    }

    @Test
    void ajaxRequestReturnsOnlyFilteredPaginatedResultsFragment() throws Exception {
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

        mockMvc.perform(get(URI.create("/travel-info?keyword=%EC%B6%95%EC%A0%9C"
                        + "&scope=DOMESTIC&contentType=FESTIVAL"
                        + "&categoryId=3&categoryId=5&sort=views&page=2"))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/fragments/list-results :: results"))
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

        verify(infoCategoryService, never()).getVisible();
    }

    @Test
    void blankLatestAndInvalidSortValuesUseCanonicalLatestOrder() throws Exception {
        when(travelInfoService.getPublicList(
                null, null, List.of(), null, "latest", 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of());

        for (String sort : List.of("", "latest", "abc")) {
            mockMvc.perform(get("/travel-info").param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("sort", "latest"))
                    .andExpect(model().attribute("listUrl", "/travel-info"));
        }

        verify(travelInfoService, org.mockito.Mockito.times(3)).getPublicList(
                null, null, List.of(), null, "latest", 0L, 12);
    }

    @Test
    void titleKeywordIsNormalizedCombinedWithFiltersAndRenderedSafely() throws Exception {
        when(travelInfoService.getPublicList(
                TravelInfoScope.INTERNATIONAL, null, List.of(2L, 5L), "파리", "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.INTERNATIONAL, null, List.of(2L, 5L), "파리"))
                .thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info")
                        .param("keyword", "  파리  ")
                        .param("scope", "INTERNATIONAL")
                        .param("categoryId", "2", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "파리"))
                .andExpect(model().attribute("listUrl",
                        "/travel-info?keyword=%ED%8C%8C%EB%A6%AC"
                                + "&scope=INTERNATIONAL&categoryId=2&categoryId=5"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"파리\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "‘파리’</strong>에 해당하는 여행정보를 찾지 못했습니다.")));

        verify(travelInfoService).getPublicList(
                TravelInfoScope.INTERNATIONAL, null, List.of(2L, 5L), "파리", "latest", 0L, 12);
        verify(travelInfoService).countPublicList(
                TravelInfoScope.INTERNATIONAL, null, List.of(2L, 5L), "파리");
    }

    @Test
    void blankKeywordIsIgnoredAndLongKeywordIsSafelyLimited() throws Exception {
        String limitedKeyword = "가".repeat(100);
        when(travelInfoService.getPublicList(null, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(0L);
        when(travelInfoService.getPublicList(
                null, null, List.of(), limitedKeyword, "latest", 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(
                null, null, List.of(), limitedKeyword)).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of());

        mockMvc.perform(get("/travel-info").param("keyword", "   \t"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/travel-info").param("keyword", "가".repeat(101)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", limitedKeyword));

        verify(travelInfoService).getPublicList(null, null, List.of(), null, "latest", 0L, 12);
        verify(travelInfoService).getPublicList(
                null, null, List.of(), limitedKeyword, "latest", 0L, 12);
    }

    @Test
    void invalidFiltersAndNonPositivePageSizeFallBackSafely() throws Exception {
        when(travelInfoService.getPublicList(null, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of());

        mockMvc.perform(get("/travel-info")
                        .param("scope", "unknown")
                        .param("contentType", "unsupported")
                        .param("categoryId", "not-a-number", "-3", "0")
                        .param("page", "-2")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scope", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("contentType", org.hamcrest.Matchers.nullValue()))
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
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info").param("categoryId", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categoryIds", List.of(999L)))
                .andExpect(model().attribute("totalCount", 0L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "조건에 맞는 여행정보가 없습니다.")));
    }

    @Test
    void guestCanOpenFestivalDetailWithAllPeriodsRichTextAndValidatedListUrl() throws Exception {
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        detail.setContent("<p><span class=\"ql-font-noto-serif-kr\">축제 본문</span></p>"
                + "<img src=\"/uploads/editor/festival.png\" width=\"600\" alt=\"축제\">");
        detail.setPeriods(List.of(
                new TravelInfoPeriodDto(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-15")),
                new TravelInfoPeriodDto(LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-25"))));
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);

        mockMvc.perform(get(URI.create(
                "/travel-info/10?returnUrl=%2Ftravel-info%3Fkeyword%3D"
                        + "%25ED%258C%258C%25EB%25A6%25AC%26scope%3Ddomestic"
                        + "%26contentType%3Dfestival%26categoryId%3D1%26categoryId%3D3"
                        + "%26categoryId%3D1%26sort%3Dviews%26page%3D2%26size%3D24")))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/detail"))
                .andExpect(model().attribute("travelInfo", detail))
                .andExpect(model().attribute("pageTitle", "공개 여행정보 | 여행정보"))
                .andExpect(model().attribute("listUrl",
                        "/travel-info?keyword=%ED%8C%8C%EB%A6%AC"
                                + "&scope=DOMESTIC&contentType=FESTIVAL"
                                + "&categoryId=1&categoryId=3&sort=views&page=2&size=24"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공개 여행정보")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("행사 기간")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-08-10")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-08-25")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"travel-info-detail-content rich-text-content\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<span class=\"ql-font-noto-serif-kr\">축제 본문</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("최종 수정")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-info-thumbnail"))));
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
        when(travelInfoService.getPublicList(null, null, List.of(), null, "latest", 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(), null)).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of());
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
        return category(3L, "계절여행", 1);
    }

    private InfoCategory category(Long id, String name, int displayOrder) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
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

    private CustomUserDetails principal(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("traveler");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }
}
