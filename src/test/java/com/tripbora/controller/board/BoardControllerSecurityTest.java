package com.tripbora.controller.board;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.BoardListDto;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.User;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.board.BoardService;
import com.tripbora.service.category.CountryCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BoardController.class)
@Import(SecurityConfig.class)
class BoardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private CountryCategoryService countryCategoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenBoardListWithoutWriteAction() throws Exception {
        when(boardService.getBoardList(null, null, "all", null, "latest", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null, "all", null)).thenReturn(0);

        mockMvc.perform(get("/board/list"))
                .andExpect(status().isOk())
                .andExpect(view().name("board/list"))
                .andExpect(model().attribute("pageTitle", "여행 커뮤니티"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여행 커뮤니티")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("board-list-actions"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("board-mobile-write-action"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/post/write\""))));
    }

    @Test
    void memberSeesCourseWriteActionBelowTheList() throws Exception {
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of());
        when(boardService.getBoardList("course", null, "all", null, "latest", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount("course", null, "all", null)).thenReturn(0);

        mockMvc.perform(get("/board/list")
                        .param("boardType", "course")
                        .with(user("member")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-list-actions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/course/write\"")))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body.indexOf("board-list-actions"))
                            .isGreaterThan(body.indexOf("board-fragment-container"));
                    assertThat(body.indexOf("board-mobile-write-action"))
                            .isLessThan(body.indexOf("board-fragment-container"));
                    assertThat(org.jsoup.Jsoup.parse(body).select("[data-board-write-open]")).hasSize(2);
                    String exportPath = System.getenv("BOARD_BROWSER_FIXTURES");
                    if (exportPath != null) {
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(exportPath));
                        java.nio.file.Files.writeString(java.nio.file.Path.of(exportPath, "member-course.html"), body);
                    }
                });
    }

    @Test
    void unsupportedSortShowsLatestAsActive() throws Exception {
        when(boardService.getBoardList(null, null, "all", null, "unsupported", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null, "all", null)).thenReturn(0);

        mockMvc.perform(get("/board/list").param("sort", "unsupported"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'latest')\" aria-pressed=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'comments')\" aria-pressed=\"false\"")));
    }

    @Test
    void bookmarkSortShowsAsActive() throws Exception {
        when(boardService.getBoardList(null, null, "all", null, "bookmarks", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null, "all", null)).thenReturn(0);

        mockMvc.perform(get("/board/list").param("sort", "bookmarks"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'latest')\" aria-pressed=\"false\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'bookmarks')\" aria-pressed=\"true\"")));
    }

    @Test
    void guestCanLoadBoardFragment() throws Exception {
        BoardListDto course = new BoardListDto();
        course.setId(20L);
        course.setBoardType("course");
        course.setTitle("제주 한 바퀴");
        course.setNickname("여행자");
        course.setCreatedAt("2026-08-07 00:18:47");
        course.setBookmarkCount(4);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of());
        when(boardService.getBoardList("course", null, "all", null, "comments", 2, 10))
                .thenReturn(List.of(course));
        when(boardService.getBoardCount("course", null, "all", null)).thenReturn(12);

        mockMvc.perform(get("/board/fragment")
                        .param("boardType", "course")
                        .param("sort", "comments")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("board/fragment :: boardListFragment"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-bookmarks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("제주 한 바퀴")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("26.08.07 00:18")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2026-08-07 00:18:47"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">작성일<"))));
    }

    @Test
    void overseasCountryFilterAcceptsOnlyCountriesFromTheSharedCourseCountryList() throws Exception {
        CountryCategory korea = country(7L, "대한민국", null);
        CountryCategory japan = country(8L, "일본", 1L);
        CountryCategory france = country(17L, "프랑스", 2L);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan, france));
        when(boardService.getBoardList("course", null, "overseas", 8L, "views", 1, 10))
                .thenReturn(List.of());
        when(boardService.getBoardCount("course", null, "overseas", 8L)).thenReturn(0);

        mockMvc.perform(get("/board/list")
                        .param("boardType", "course")
                        .param("scope", "overseas")
                        .param("countryId", "8")
                        .param("sort", "views"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scope", "overseas"))
                .andExpect(model().attribute("countryId", 8L))
                .andExpect(model().attribute("overseasCourseCountries", List.of(japan, france)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("전체 국가")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("일본")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">대한민국</option>"))));
    }

    @Test
    void continentOrDomesticRegionIdIsIgnoredAsAnOverseasCountryFilter() throws Exception {
        CountryCategory korea = country(7L, "대한민국", null);
        CountryCategory japan = country(8L, "일본", 1L);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(korea, japan));

        mockMvc.perform(get("/board/list")
                        .param("boardType", "course")
                        .param("scope", "overseas")
                        .param("countryId", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("countryId", org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/board/list")
                        .param("boardType", "course")
                        .param("scope", "overseas")
                        .param("countryId", "70"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("countryId", org.hamcrest.Matchers.nullValue()));

        verify(boardService, org.mockito.Mockito.times(2))
                .getBoardList("course", null, "overseas", null, "latest", 1, 10);
        verify(boardService, org.mockito.Mockito.times(2))
                .getBoardCount("course", null, "overseas", null);
    }

    private CountryCategory country(Long id, String name, Long parentId) {
        CountryCategory country = new CountryCategory();
        country.setId(id);
        country.setRegionName(name);
        country.setParentId(parentId);
        return country;
    }

    @Test
    void smartphoneSizeUsesEightRowsAndMatchingPageCountForEveryBoardAndSort() throws Exception {
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(country(8L, "일본", 1L)));
        String[][] boards = {{"", "", "all"}, {"post", "question", "all"}, {"post", "tip", "all"},
                {"course", "", "all"}, {"course", "", "domestic"}, {"course", "", "overseas"}};
        String exportPath = System.getenv("BOARD_BROWSER_FIXTURES");
        for (int board = 0; board < boards.length; board++) {
            String type = boards[board][0].isEmpty() ? null : boards[board][0];
            String postType = boards[board][1].isEmpty() ? null : boards[board][1];
            String scope = boards[board][2];
            for (String sort : List.of("latest", "oldest", "views", "comments", "bookmarks")) {
                for (int page = 1; page <= 3; page++) {
                    List<BoardListDto> rows = new java.util.ArrayList<>();
                    int count = page == 1 ? 8 : page == 2 ? 1 : 0;
                    for (int row = 0; row < count; row++) {
                        BoardListDto item = new BoardListDto();
                        item.setId((long) row + 1);
                        item.setBoardType(type == null ? (row % 2 == 0 ? "course" : "post") : type);
                        item.setPostType("tip".equals(postType) ? "TIP" : "QUESTION");
                        item.setTitle(row % 3 == 0 ? "서울 궁궐과 한강을 따라 즐기는 아주 긴 하루 여행 코스와 맛집 추천"
                                : row % 3 == 1 ? "A very long travel title with SupercalifragilisticexpialidociousWithoutAnySpaces1234567890"
                                : "東京の街をゆっくり歩く長い旅行コースとおすすめの場所について");
                        item.setNickname("귀여운곰돌이와아주긴작성자이름");
                        item.setUserId(1L);
                        item.setCreatedAt("2026-09-17 00:36:00");
                        item.setViews(28);
                        item.setCommentCount(4);
                        item.setBookmarkCount(0);
                        rows.add(item);
                    }
                    when(boardService.getBoardList(type, postType, scope, null, sort, page, 8)).thenReturn(rows);
                    when(boardService.getBoardCount(type, postType, scope, null)).thenReturn(page == 3 ? 0 : 9);
                    for (String endpoint : List.of("list", "fragment")) {
                        var request = get("/board/" + endpoint).param("scope", scope).param("sort", sort)
                                .param("page", String.valueOf(page)).param("size", "8");
                        if (type != null) request.param("boardType", type);
                        if (postType != null) request.param("postType", postType);
                        var response = mockMvc.perform(request).andExpect(status().isOk())
                                .andExpect(model().attribute("pageSize", 8))
                                .andExpect(model().attribute("totalPages", page == 3 ? 0 : 2))
                                .andReturn().getResponse().getContentAsString();
                        assertThat(org.jsoup.Jsoup.parse(response).select(".board-list-row")).hasSize(count);
                        assertThat(response).contains("data-page-size=\"8\"");
                        if (exportPath != null) {
                            java.nio.file.Path directory = java.nio.file.Path.of(exportPath);
                            java.nio.file.Files.createDirectories(directory);
                            java.nio.file.Files.writeString(directory.resolve(board + "-" + sort + "-" + page + "-" + endpoint + ".html"), response);
                        }
                    }
                }
            }
        }
    }
}
