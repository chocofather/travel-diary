package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.destination.DestinationService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationService destinationService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void regularUserCannotOpenTheAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboardUsesOnlyDetailButtonsAcrossFourManagementCards() throws Exception {
        var document = adminPage();

        assertThat(document.select(".admin-dashboard h1").text()).isEqualTo("관리자 대시보드");
        assertThat(document.select(".admin-dashboard-card")).hasSize(4);
        assertThat(document.select(".admin-dashboard-card > h2").eachText())
                .containsExactly("여행지 관리", "여행정보 관리", "이벤트 관리", "고객지원 관리");
        assertThat(document.select(".admin-dashboard-entry, .admin-dashboard-entry-arrow"))
                .isEmpty();
        assertThat(document.select(".admin-dashboard-actions a").eachAttr("href"))
                .containsExactlyElementsOf(List.of(
                        "/admin/destinations",
                        "/admin/categories",
                        "/admin/region-categories",
                        "/admin/amenities/list",
                        "/admin/travel-info",
                        "/admin/info-categories",
                        "/admin/event/list",
                        "/admin/notices",
                        "/admin/faqs",
                        "/admin/inquiries"));
        assertThat(document.select(".admin-dashboard-card:last-child p").text())
                .isEqualTo("공지사항과 FAQ, 사용자 문의를 관리합니다.");
        assertThat(document.select(".admin-dashboard-card a a")).isEmpty();
    }

    @Test
    void adminLayoutKeepsSiteLogoutAndSidebarRoutes() throws Exception {
        var document = adminPage();

        assertThat(document.select(".admin-topbar-actions a[href='/']").text())
                .isEqualTo("사이트 보기");
        assertThat(document.select(".admin-topbar-actions form[action='/logout'][method=post]"))
                .hasSize(1);
        assertThat(document.select(".admin-nav a").eachAttr("href"))
                .contains(
                        "/admin",
                        "/admin/destinations",
                        "/admin/categories",
                        "/admin/region-categories",
                        "/admin/amenities/list",
                        "/admin/travel-info",
                        "/admin/info-categories",
                        "/admin/event/list",
                        "/admin/notices",
                        "/admin/faqs",
                        "/admin/inquiries");
    }

    @Test
    void dashboardStylesKeepTheExistingTwoColumnBlueCardLayout() throws IOException {
        String css = resource("/static/css/admin-layout.css");

        assertThat(css)
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("var(--admin-blue)")
                .contains(".admin-dashboard-card h2")
                .doesNotContain(".admin-dashboard-entry", ".admin-dashboard-entry-arrow");
    }

    private org.jsoup.nodes.Document adminPage() throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
