package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.dto.ModeratedContentDto;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.moderation.ContentModerationService;
import com.example.travlediary.service.moderation.ModerationValidationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminContentModerationController.class)
@Import(SecurityConfig.class)
class AdminContentModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentModerationService contentModerationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    /* === 조치된 콘텐츠 관리 목록 === */

    @Test
    void listShowsOnlyAdminModeratedContentWithItsOriginInfo() throws Exception {
        when(contentModerationService.countModeratedContents(null, null)).thenReturn(2L);
        when(contentModerationService.getModeratedContents(null, null, 0L, 20))
                .thenReturn(List.of(moderatedPost(), moderatedComment()));

        Document document = adminPage("/admin/contents");

        assertThat(document.select(".admin-contents-table thead th").eachText())
                .containsExactly("유형", "콘텐츠", "작성자", "조치 사유", "조치 관리자", "조치일", "복구");
        assertThat(document.select(".admin-content-type").eachText())
                .containsExactly("게시글", "게시글 댓글");
        // 글과 댓글을 배지 색으로 구분한다
        assertThat(document.select(".admin-content-type.is-article")).hasSize(1);
        assertThat(document.select(".admin-content-type.is-comment")).hasSize(1);
        assertThat(document.select(".admin-content-origin").eachText())
                .containsExactly("게시글 #3", "원본 게시글 댓글 #7 · 댓글 #8");
        assertThat(document.select(".admin-content-reason").eachText())
                .containsExactly("욕설", "스팸");
        assertThat(document.select(".admin-contents-table tbody td").eachText())
                .contains("master");
    }

    @Test
    void listForwardsTypeFilterAndKeywordToTheService() throws Exception {
        when(contentModerationService.countModeratedContents(
                ModerationTargetType.COURSE_COMMENT, "여행")).thenReturn(0L);
        when(contentModerationService.getModeratedContents(
                ModerationTargetType.COURSE_COMMENT, "여행", 0L, 20)).thenReturn(List.of());

        mockMvc.perform(get("/admin/contents")
                        .param("targetType", "COURSE_COMMENT")
                        .param("keyword", "  여행  ")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/contents/list"))
                .andExpect(model().attribute("currentType", "COURSE_COMMENT"))
                .andExpect(model().attribute("keyword", "여행"));

        verify(contentModerationService).getModeratedContents(
                ModerationTargetType.COURSE_COMMENT, "여행", 0L, 20);
    }

    @Test
    void typeSelectAutoSubmitsWhileKeywordStillNeedsTheSearchButton() throws Exception {
        when(contentModerationService.countModeratedContents(
                ModerationTargetType.POST, "여행")).thenReturn(1L);
        when(contentModerationService.getModeratedContents(
                ModerationTargetType.POST, "여행", 0L, 20)).thenReturn(List.of(moderatedPost()));

        Document document = Jsoup.parse(mockMvc.perform(get("/admin/contents")
                        .param("targetType", "POST").param("keyword", "여행")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // 유형 select 는 같은 GET 폼 안에 있어 자동 전송돼도 검색어가 함께 유지된다
        var form = document.selectFirst("#admin-content-filter-form");
        assertThat(form.attr("method")).isEqualToIgnoringCase("get");
        assertThat(form.selectFirst("select[name=targetType]")).isNotNull();
        assertThat(form.selectFirst("input[name=keyword]")).isNotNull();
        // 선택한 유형과 검색어가 화면에 그대로 남는다
        assertThat(form.selectFirst("select[name=targetType] option[selected]").val())
                .isEqualTo("POST");
        assertThat(form.selectFirst("input[name=keyword]").val()).isEqualTo("여행");
        // 검색어는 조회 버튼(또는 Enter)으로 검색하고 초기화 링크도 유지된다
        assertThat(form.select("button[type=submit]").text()).isEqualTo("조회");
        assertThat(form.select("a[href='/admin/contents']").text()).isEqualTo("초기화");
    }

    @Test
    void unknownFilterFallsBackToAllAndPagingMovesTheOffset() throws Exception {
        when(contentModerationService.countModeratedContents(null, null)).thenReturn(45L);
        when(contentModerationService.getModeratedContents(eq(null), eq(null), anyLong(), anyInt()))
                .thenReturn(List.of(moderatedPost()));

        mockMvc.perform(get("/admin/contents")
                        .param("targetType", "UNKNOWN")
                        .param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentType", "ALL"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 3));

        verify(contentModerationService).getModeratedContents(null, null, 20L, 20);
    }

    @Test
    void emptyListShowsTheEmptyMessage() throws Exception {
        when(contentModerationService.countModeratedContents(null, null)).thenReturn(0L);
        when(contentModerationService.getModeratedContents(null, null, 0L, 20))
                .thenReturn(List.of());

        Document document = adminPage("/admin/contents");

        assertThat(document.selectFirst(".admin-table-empty").text())
                .isEqualTo("조치 중인 콘텐츠가 없습니다.");
        assertThat(document.select(".admin-content-pagination")).isEmpty();
    }

    @Test
    void everyRowOffersRestoreWithAnOptionalReasonBackToTheSameFilter() throws Exception {
        when(contentModerationService.countModeratedContents(
                ModerationTargetType.POST, "글")).thenReturn(1L);
        when(contentModerationService.getModeratedContents(
                ModerationTargetType.POST, "글", 0L, 20)).thenReturn(List.of(moderatedPost()));

        Document document = Jsoup.parse(mockMvc.perform(get("/admin/contents")
                        .param("targetType", "POST").param("keyword", "글")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        var form = document.selectFirst(".admin-content-restore-form");
        assertThat(form.attr("action")).isEqualTo("/admin/contents/POST/3/restore");
        assertThat(form.attr("method")).isEqualToIgnoringCase("post");
        assertThat(form.selectFirst("input[name=reason]").hasAttr("required")).isFalse();
        assertThat(form.selectFirst("input[name=redirect]").val())
                .contains("/admin/contents").contains("targetType=POST").contains("keyword=");
    }

    @Test
    void listRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/contents").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidePassesTargetReasonAndAdminIdThenReturnsToTheContentPage() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "욕설")
                        .param("adminNote", "내부 메모")
                        .param("redirect", "/board/list?boardType=post"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/board/list?boardType=post"));

        ArgumentCaptor<ContentModerationForm> captor =
                ArgumentCaptor.forClass(ContentModerationForm.class);
        verify(contentModerationService).hide(eq(ModerationTargetType.POST), eq(3L),
                captor.capture(), eq(1L));
        assertThat(captor.getValue().getReason()).isEqualTo("욕설");
        assertThat(captor.getValue().getAdminNote()).isEqualTo("내부 메모");
    }

    @Test
    void everySupportedTargetTypeHasAnEndpoint() throws Exception {
        for (ModerationTargetType type : ModerationTargetType.values()) {
            mockMvc.perform(post("/admin/contents/" + type.name() + "/3/hide")
                            .with(user(admin())).with(csrf())
                            .param("reason", "사유"))
                    .andExpect(status().is3xxRedirection());
            verify(contentModerationService).hide(eq(type), eq(3L), any(), eq(1L));
        }
    }

    @Test
    void restoreCallsTheServiceWithTheRestoreReason() throws Exception {
        mockMvc.perform(post("/admin/contents/POST_COMMENT/8/restore")
                        .with(user(admin())).with(csrf())
                        .param("reason", "오탐")
                        .param("redirect", "/post/3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/post/3"));

        verify(contentModerationService).restore(eq(ModerationTargetType.POST_COMMENT), eq(8L),
                any(), eq(1L));
    }

    @Test
    void unknownTargetTypeIsNotFound() throws Exception {
        mockMvc.perform(post("/admin/contents/UNKNOWN/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isNotFound());

        verify(contentModerationService, never()).hide(any(), anyLong(), any(), anyLong());
    }

    @Test
    void externalRedirectTargetsFallBackToHome() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유")
                        .param("redirect", "https://evil.example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void rejectedModerationReturnsBadRequest() throws Exception {
        doThrow(new ModerationValidationException(null, "이미 조치 중인 콘텐츠입니다."))
                .when(contentModerationService).hide(eq(ModerationTargetType.POST), eq(3L),
                        any(), eq(1L));

        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin())).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderationEndpointsRequireCsrfAndAdminRole() throws Exception {
        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user(admin()))
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/contents/POST/3/hide")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("reason", "사유"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/contents/POST/3/restore")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());

        verify(contentModerationService, never()).hide(any(), anyLong(), any(), anyLong());
        verify(contentModerationService, never()).restore(any(), anyLong(), any(), anyLong());
    }

    private Document adminPage(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ModeratedContentDto moderatedPost() {
        ModeratedContentDto content = new ModeratedContentDto();
        content.setModerationId(50L);
        content.setTargetType(ModerationTargetType.POST);
        content.setTargetId(3L);
        content.setTitle("여름 여행기");
        content.setContentSnippet("본문 일부");
        content.setAuthorUserId(9L);
        content.setAuthorName("여행자");
        content.setReason("욕설");
        content.setAdminName("master");
        content.setCreatedAt(Timestamp.valueOf("2026-08-15 10:00:00"));
        return content;
    }

    private ModeratedContentDto moderatedComment() {
        ModeratedContentDto content = new ModeratedContentDto();
        content.setModerationId(51L);
        content.setTargetType(ModerationTargetType.POST_COMMENT);
        content.setTargetId(8L);
        content.setParentId(7L);
        content.setContentSnippet("댓글 내용");
        content.setAuthorUserId(10L);
        content.setAuthorName("사용자");
        content.setReason("스팸");
        content.setAdminName("master");
        content.setCreatedAt(Timestamp.valueOf("2026-08-15 11:00:00"));
        return content;
    }

    private CustomUserDetails admin() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("master");
        adminUser.setUserPassword("password");
        adminUser.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(adminUser);
    }
}
