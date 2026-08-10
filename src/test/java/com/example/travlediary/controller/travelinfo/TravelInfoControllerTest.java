package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(travelInfoService.getPublicList(null, null, List.of(), 0L, 12))
                .thenReturn(List.of(festival, general));
        when(travelInfoService.countPublicList(null, null, List.of())).thenReturn(2L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/list"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("totalCount", 2L))
                .andExpect(model().attribute("pageTitle", "여행정보"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서울 봄 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/uploads/travel-info/thumbnails/festival.jpg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-03")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("등록된 이미지가 없습니다")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/travel-info/10\""))))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.selectFirst(
                            "button[data-filter-name=categoryId][data-filter-value='']"))
                            .isNotNull()
                            .extracting(element -> element.attr("aria-pressed"))
                            .isEqualTo("true");
                });
    }

    @Test
    void filtersAndPaginationAreNormalizedAndPassedToTheModel() throws Exception {
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, List.of(3L), 48L, 48))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, List.of(3L)))
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
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, List.of(3L), 48L, 48);
    }

    @Test
    void repeatedCategoriesAreDeduplicatedAndCombinedWithScopeAndHiddenContentType() throws Exception {
        List<Long> selectedCategoryIds = List.of(1L, 3L, 5L);
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds, 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds)).thenReturn(0L);
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
                selectedCategoryIds, 0L, 12);
        verify(travelInfoService).countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL,
                selectedCategoryIds);
    }

    @Test
    void directFestivalUrlKeepsBackendFilterAndFestivalCardPresentation() throws Exception {
        TravelInfoListItemDto festival = item(10L, "여름 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, null);
        festival.setStartDate(LocalDate.parse("2026-08-01"));
        festival.setEndDate(LocalDate.parse("2026-08-03"));
        when(travelInfoService.getPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of(), 0L, 12))
                .thenReturn(List.of(festival));
        when(travelInfoService.countPublicList(
                null, TravelInfoContentType.FESTIVAL, List.of())).thenReturn(1L);
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
                null, TravelInfoContentType.FESTIVAL, List.of(), 0L, 12);
    }

    @Test
    void ajaxRequestReturnsOnlyFilteredPaginatedResultsFragment() throws Exception {
        TravelInfoListItemDto festival = item(10L, "국내 여름 축제", TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, null);
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, List.of(3L, 5L), 12L, 12))
                .thenReturn(List.of(festival));
        when(travelInfoService.countPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, List.of(3L, 5L)))
                .thenReturn(25L);

        mockMvc.perform(get("/travel-info")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "FESTIVAL")
                        .param("categoryId", "3", "5")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("travel-info/fragments/list-results :: results"))
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attribute("contentType", TravelInfoContentType.FESTIVAL))
                .andExpect(model().attribute("categoryIds", List.of(3L, 5L)))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 3))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"travel-info-results\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("국내 여름 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "contentType=FESTIVAL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "categoryId=3&amp;categoryId=5")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-info-heading"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-info-filters"))));

        verify(infoCategoryService, never()).getVisible();
    }

    @Test
    void invalidFiltersAndNonPositivePageSizeFallBackSafely() throws Exception {
        when(travelInfoService.getPublicList(null, null, List.of(), 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of())).thenReturn(0L);
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
        when(travelInfoService.getPublicList(null, null, List.of(999L), 0L, 12))
                .thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of(999L))).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of(category()));

        mockMvc.perform(get("/travel-info").param("categoryId", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categoryIds", List.of(999L)))
                .andExpect(model().attribute("totalCount", 0L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "조건에 맞는 여행정보가 없습니다.")));
    }

    @Test
    void onlyTheListGetIsPublicAndAdminPolicyStillRejectsRegularUsers() throws Exception {
        when(travelInfoService.getPublicList(null, null, List.of(), 0L, 12)).thenReturn(List.of());
        when(travelInfoService.countPublicList(null, null, List.of())).thenReturn(0L);
        when(infoCategoryService.getVisible()).thenReturn(List.of());

        mockMvc.perform(get("/travel-info"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/travel-info"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/travel-info/10"))
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
}
