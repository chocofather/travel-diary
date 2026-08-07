package com.example.travlediary.controller.board;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.board.BoardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
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
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenBoardListWithoutWriteAction() throws Exception {
        when(boardService.getBoardList(null, null, "latest", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null)).thenReturn(0);

        mockMvc.perform(get("/board/list"))
                .andExpect(status().isOk())
                .andExpect(view().name("board/list"))
                .andExpect(model().attribute("pageTitle", "여행 커뮤니티"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여행 커뮤니티")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("board-list-actions"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/post/write\""))));
    }

    @Test
    void memberSeesCourseWriteActionBelowTheList() throws Exception {
        User member = new User();
        when(userMapper.findByUsername("member")).thenReturn(member);
        when(boardService.getBoardList("course", null, "latest", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount("course", null)).thenReturn(0);

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
                });
    }

    @Test
    void unsupportedSortShowsLatestAsActive() throws Exception {
        when(boardService.getBoardList(null, null, "unsupported", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null)).thenReturn(0);

        mockMvc.perform(get("/board/list").param("sort", "unsupported"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'latest')\" aria-pressed=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "onclick=\"loadBoardList(1, 'comments')\" aria-pressed=\"false\"")));
    }

    @Test
    void bookmarkSortShowsAsActive() throws Exception {
        when(boardService.getBoardList(null, null, "bookmarks", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCount(null, null)).thenReturn(0);

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
        when(boardService.getBoardList("course", null, "comments", 2, 10)).thenReturn(List.of(course));
        when(boardService.getBoardCount("course", null)).thenReturn(12);

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
}
