package com.example.travlediary.controller.search;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.search.GlobalSearchService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(GlobalSearchController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class GlobalSearchControllerTest {

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

    @Test
    void searchRendersResultsAndPreservesQueryAndTypeInPagination() throws Exception {
        GlobalSearchResultDto item = new GlobalSearchResultDto();
        item.setType("community");
        item.setId(7L);
        item.setTitle("제주 여행 질문");
        item.setSummary("일정이 궁금합니다.");
        item.setCreatedAt(Timestamp.valueOf("2026-08-14 10:00:00"));
        item.setDetailUrl("/post/7");
        GlobalSearchPage page = new GlobalSearchPage(
                "제주", "community", List.of(item), 21, 2, 10, 3, 1, 3);
        when(globalSearchService.search("제주", "community", 2, SupportedLanguage.KOREAN))
                .thenReturn(page);

        mockMvc.perform(get("/search")
                        .param("q", "제주")
                        .param("type", "community")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("searchPage", page))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".global-search-result a[href='/post/7']")).hasSize(1);
                    assertThat(document.select(".global-search-result").text())
                            .contains("커뮤니티", "제주 여행 질문", "일정이 궁금합니다.");
                    assertThat(document.select(".global-search-pagination a[href*='q=%EC%A0%9C%EC%A3%BC'][href*='type=community']"))
                            .isNotEmpty();
                });

        verify(globalSearchService).search("제주", "community", 2, SupportedLanguage.KOREAN);
    }

    @Test
    void malformedPageIsSafelyNormalized() throws Exception {
        GlobalSearchPage page = new GlobalSearchPage(
                null, "all", List.of(), 0, 1, 10, 0, 1, 0);
        when(globalSearchService.search(null, "all", 1, SupportedLanguage.KOREAN))
                .thenReturn(page);

        mockMvc.perform(get("/search").param("page", "invalid"))
                .andExpect(status().isOk());

        verify(globalSearchService).search(null, "all", 1, SupportedLanguage.KOREAN);
    }

    @Test
    void destinationResultShowsExistingMainImageWithoutPlaceholder() throws Exception {
        GlobalSearchResultDto destination = new GlobalSearchResultDto();
        destination.setType("destination");
        destination.setId(3L);
        destination.setTitle("제주 성산일출봉");
        destination.setSummary("제주의 대표 여행지");
        destination.setDetailUrl("/destinations/3");
        destination.setThumbnailUrl("/uploads/destinations/seongsan.jpg");
        GlobalSearchResultDto destinationWithoutImage = new GlobalSearchResultDto();
        destinationWithoutImage.setType("destination");
        destinationWithoutImage.setId(4L);
        destinationWithoutImage.setTitle("이미지 없는 여행지");
        destinationWithoutImage.setSummary("텍스트만 표시되는 결과");
        destinationWithoutImage.setDetailUrl("/destinations/4");
        GlobalSearchPage page = new GlobalSearchPage(
                "제주", "destination", List.of(destination, destinationWithoutImage), 2, 1, 10, 1, 1, 1);
        when(globalSearchService.search("제주", "destination", 1, SupportedLanguage.KOREAN))
                .thenReturn(page);

        mockMvc.perform(get("/search")
                        .param("q", "제주")
                        .param("type", "destination"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            "img.global-search-thumbnail[src='/uploads/destinations/seongsan.jpg'][alt='제주 성산일출봉']"))
                            .hasSize(1);
                    assertThat(document.select(".global-search-result")).hasSize(2);
                    assertThat(document.select("img.global-search-thumbnail")).hasSize(1);
                    assertThat(document.select("img.global-search-thumbnail[src*='no-image']")).isEmpty();
                });
    }

    @Test
    void eventResultsShowExistingImageAndPeriodWithoutEmptyPlaceholder() throws Exception {
        GlobalSearchResultDto event = new GlobalSearchResultDto();
        event.setType("event");
        event.setId(12L);
        event.setTitle("여름 여행 이벤트");
        event.setSummary("여름 여행을 위한 특별 이벤트입니다.");
        event.setDetailUrl("/events/12");
        event.setThumbnailUrl("/uploads/events/summer.jpg");
        event.setStartDate(LocalDate.of(2026, 8, 1));
        event.setEndDate(LocalDate.of(2026, 8, 31));
        GlobalSearchResultDto eventWithoutImage = new GlobalSearchResultDto();
        eventWithoutImage.setType("event");
        eventWithoutImage.setId(13L);
        eventWithoutImage.setTitle("이미지 없는 이벤트");
        eventWithoutImage.setSummary("텍스트만 표시되는 이벤트입니다.");
        eventWithoutImage.setDetailUrl("/events/13");
        eventWithoutImage.setStartDate(LocalDate.of(2026, 9, 1));
        eventWithoutImage.setEndDate(LocalDate.of(2026, 9, 30));
        GlobalSearchPage page = new GlobalSearchPage(
                "이벤트", "event", List.of(event, eventWithoutImage), 2, 1, 10, 1, 1, 1);
        when(globalSearchService.search("이벤트", "event", 1, SupportedLanguage.KOREAN))
                .thenReturn(page);

        mockMvc.perform(get("/search")
                        .param("q", "이벤트")
                        .param("type", "event"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            "img.global-search-thumbnail[src='/uploads/events/summer.jpg'][alt='여름 여행 이벤트']"))
                            .hasSize(1);
                    assertThat(document.select(".global-search-result")).hasSize(2);
                    assertThat(document.select("img.global-search-thumbnail")).hasSize(1);
                    assertThat(document.select(".global-search-event-period").text())
                            .contains("2026-08-01 ~ 2026-08-31", "2026-09-01 ~ 2026-09-30");
                    assertThat(document.select("img.global-search-thumbnail[src*='no-image']")).isEmpty();
                });
    }
}
