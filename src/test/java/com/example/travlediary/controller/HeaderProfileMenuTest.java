package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.controller.recommend.RandomTravelController;
import com.example.travlediary.repository.user.UserMapper;
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

@WebMvcTest(RandomTravelController.class)
@Import(SecurityConfig.class)
class HeaderProfileMenuTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void guestDoesNotReceiveProfileOrAdminControls() throws Exception {
        var document = page();

        assertThat(document.select("#profile-menu-toggle, #profile-menu")).isEmpty();
        assertThat(document.select("a[href='/admin'], .admin-link")).isEmpty();
    }

    @Test
    void regularUserReceivesMypageAndPostLogoutWithoutAdminEntry() throws Exception {
        var document = page("member", "USER");

        assertThat(document.select(
                "#profile-menu-toggle[aria-controls=profile-menu][aria-expanded=false]"))
                .hasSize(1);
        assertThat(document.select("#profile-menu[hidden]")).hasSize(1);
        assertThat(document.select("#profile-menu a[href='/mypage']").text())
                .isEqualTo("마이페이지");
        assertThat(document.select("#profile-menu a[href='/admin']")).isEmpty();
        assertThat(document.select("#profile-menu form[action='/logout'][method=post]"))
                .hasSize(1);
        assertThat(document.select(
                "#profile-menu form[action='/logout'] input[name=redirect]"))
                .hasSize(1);
        assertThat(document.select("#auth-area > .logout-form, .admin-link")).isEmpty();
    }

    @Test
    void adminReceivesServerRenderedAdminEntryInsideTheProfileMenu() throws Exception {
        var document = page("admin", "ADMIN");

        assertThat(document.select("#profile-menu a.profile-menu-admin[href='/admin']").text())
                .isEqualTo("관리자 페이지");
        assertThat(document.select("#auth-area > a[href='/admin']")).isEmpty();
    }

    @Test
    void headerNavigationRemainsUnchanged() throws Exception {
        assertThat(page().select(".main-menu > .menu-item > a").eachText())
                .containsExactlyElementsOf(List.of(
                        "국내", "해외", "여행 커뮤니티", "여행정보",
                        "여행기록", "고객센터", "이벤트"));
    }

    @Test
    void profileMenuScriptCoversToggleOutsideClickAndEscapeFocusRestoration()
            throws IOException {
        String script = resource("/static/js/main.js");
        String css = resource("/static/css/style.css");

        assertThat(script)
                .contains("profile-menu-toggle", "profile-menu")
                .contains("setProfileMenuOpen")
                .contains("profileMenu.hidden")
                .contains("aria-expanded")
                .contains("!profileMenu.hidden")
                .contains("!profileMenuContainer.contains(e.target)")
                .contains("e.key === 'Escape'")
                .contains("profileToggle.focus()")
                .doesNotContain("location.href = '/mypage'");
        assertThat(css)
                .contains(".profile-menu")
                .contains("max-width: calc(100vw - 24px)")
                .contains(".profile-menu-item:hover")
                .contains(".profile-menu-item:focus-visible");
    }

    @Test
    void headerActionsGainResponsiveRightPaddingWithoutChangingNavigationColumns()
            throws IOException {
        String css = resource("/static/css/style.css");

        assertThat(css)
                .contains("padding-right: clamp(8px, 2vw, 24px)")
                .contains("grid-template-columns: repeat(var(--columns), 1fr)");
    }

    private org.jsoup.nodes.Document page() throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/random-travel"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private org.jsoup.nodes.Document page(String username, String role) throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/random-travel")
                        .with(user(username).roles(role)))
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
