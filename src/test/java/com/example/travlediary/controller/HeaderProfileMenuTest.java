package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.controller.recommend.RandomTravelController;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
    void aNarrowScreenGetsAMenuButtonBesideSearchAndProfile() throws Exception {
        org.jsoup.nodes.Document page = page();

        // 실제 button 이고 열림 상태를 알린다
        assertThat(page.select("button#site-menu-toggle")).hasSize(1);
        assertThat(page.selectFirst("button#site-menu-toggle").attr("aria-expanded"))
                .isEqualTo("false");
        assertThat(page.selectFirst("button#site-menu-toggle").attr("aria-controls"))
                .isEqualTo("site-menu");
        assertThat(page.selectFirst("button#site-menu-toggle").attr("aria-label")).isNotBlank();

        // 검색·프로필과 같은 줄에 있어 좁은 화면에서도 셋 다 닿는다
        assertThat(page.select(".search-box > #site-menu-toggle")).hasSize(1);
        assertThat(page.select(".search-box #search-toggle")).hasSize(1);
        assertThat(page.select(".search-box #auth-area")).hasSize(1);

        // 메뉴 판은 닫힌 채로 온다
        assertThat(page.selectFirst("#site-menu").hasAttr("hidden")).isTrue();
    }

    @Test
    void theNarrowMenuLeadsToExactlyTheSamePlacesAsTheWideOne() throws Exception {
        org.jsoup.nodes.Document page = page();

        // 두 메뉴는 같은 fragment 를 쓰므로 주소가 갈라질 수 없다
        assertThat(page.select("#site-menu .site-menu-entry > a").eachAttr("href"))
                .containsExactlyElementsOf(
                        page.select(".main-menu > .menu-item > a").eachAttr("href"));
        assertThat(page.select("#site-menu .site-menu-entry > a").eachText())
                .containsExactlyElementsOf(List.of(
                        "국내", "해외", "여행 커뮤니티", "여행정보",
                        "여행기록", "고객센터", "이벤트"));

        // 넓은 화면에서 hover 로만 닿던 하위 링크도 빠지지 않는다
        List<String> submenuLinks = page.select(".global-submenu a").eachAttr("href");
        List<String> narrowLinks = page.select("#site-menu .site-menu-entry ul a").eachAttr("href");
        assertThat(narrowLinks).containsExactlyElementsOf(submenuLinks);
        assertThat(narrowLinks).contains("/travel-plans");
    }

    @Test
    void theNarrowMenuReadsInOneDirectionWithoutRepeatingItself() throws Exception {
        org.jsoup.nodes.Document page = page();

        // 1차 메뉴가 제목 자리를 겸한다. 같은 이름이 두 번 나오지 않는다
        List<String> everyLabel = page.select("#site-menu a").eachText();
        assertThat(everyLabel).doesNotHaveDuplicates();

        // 하위 링크는 자기 1차 메뉴 안에 들어 있다
        org.jsoup.nodes.Element community = page.select("#site-menu .site-menu-entry").stream()
                .filter(entry -> "여행 커뮤니티".equals(entry.selectFirst("a").text()))
                .findFirst().orElseThrow();
        assertThat(community.select("ul a").eachText())
                .containsExactly("여행 질문", "여행 팁", "나의 여행코스");

        // 하위 링크가 없는 항목은 목록만 있고 빈 껍데기가 붙지 않는다
        org.jsoup.nodes.Element domestic = page.selectFirst("#site-menu .site-menu-entry");
        assertThat(domestic.selectFirst("a").text()).isEqualTo("국내");
        assertThat(domestic.select("ul")).isEmpty();
    }

    @Test
    void theNarrowMenuStacksVerticallyAtEveryWidthItIsUsed() throws IOException {
        String css = resource("/static/css/style.css");

        // 1차 메뉴를 가로로 늘어놓지 않는다. 늘어놓으면 좁은 폭에서 다시 접힌다
        assertThat(between(css, ".site-menu-list {", "}"))
                .doesNotContain("display: flex")
                .doesNotContain("flex-wrap");
        assertThat(between(css, ".site-menu-entry > a {", "}"))
                .contains("display: block")
                .contains("font-weight: 600");

        // 하위 링크는 들여쓰기와 크기로 위계를 낮춘다
        String sublist = between(css, ".site-menu-entry ul {", "}");
        assertThat(sublist).contains("padding: 0 0 10px 12px");
        assertThat(between(css, ".site-menu-entry ul li a {", "}"))
                .contains("font-size: 14px")
                .contains("font-weight: 400");

        // 가로로 늘어놓던 예전 구조는 남아 있지 않다
        assertThat(css).doesNotContain(".site-menu-group");

        // 모바일도 같은 구조를 쓰고 여백·글자만 다르다
        String mobile = between(css, "@media (max-width: 767px) {", "/* ---------- 검색창");
        assertThat(mobile)
                .contains(".site-menu-entry > a")
                .doesNotContain("display: flex");
    }

    @Test
    void theNarrowMenuScrollsInsideItselfInsteadOfStretchingThePage() throws IOException {
        String css = resource("/static/css/style.css");

        String panel = between(css, ".site-menu {", "}");
        assertThat(panel)
                .contains("max-height: calc(100vh - 100px)")
                .contains("overflow-y: auto")
                // 판이 화면 너비 안에 들어가 가로 스크롤이 생기지 않는다
                .contains("width: 100%")
                .contains("box-sizing: border-box");
    }

    @Test
    void theHeaderStaysOneRowUntilTheMenuPanelIsOpened() throws IOException {
        String css = resource("/static/css/style.css");

        // 메뉴 글자가 중간에서 끊기지 않는다
        assertThat(css).contains("white-space: nowrap");

        // 펼쳐 둔 메뉴가 들어가지 않는 폭부터는 통째로 감추고 ☰ 로 바꾼다.
        // (기존 좁은 화면 규칙은 열 수를 줄여 메뉴를 여러 줄로 쌓았다)
        String compact = between(css, "@media (max-width: 1199px) {", "@media (max-width: 767px)");
        assertThat(compact)
                .contains(".nav-grid")
                .contains(".global-submenu")
                .contains("display: none !important")
                .contains(".site-menu-toggle {\n        display: flex;");

        // 모바일 구간도 있다
        assertThat(css).contains("@media (max-width: 767px)");

        // 메뉴 판은 헤더 아래로 펼쳐진다. 헤더 자체 높이는 그대로다
        assertThat(between(css, ".site-menu {", "}"))
                .contains("position: absolute")
                .contains("top: 100px");
        assertThat(css).contains(".site-menu[hidden]");
    }

    @Test
    void theMenuButtonOpensClosesAndRestoresFocus() throws IOException {
        String script = resource("/static/js/main.js");

        assertThat(script)
                .contains("site-menu-toggle", "site-menu")
                .contains("setSiteMenuOpen")
                .contains("siteMenu.hidden")
                .contains("aria-expanded")
                // 바깥 클릭과 Esc 로 닫힌다
                .contains("siteMenu.contains(e.target)")
                .contains("e.key === 'Escape' && !siteMenu.hidden")
                .contains("siteMenuToggle.focus()")
                // 넓은 화면으로 돌아가면 판을 닫는다
                .contains("window.innerWidth > 1199");
        // 새 UI 프레임워크를 들이지 않는다
        assertThat(script).doesNotContain("bootstrap").doesNotContain("import ");
    }

    @Test
    void thisChangeDoesNotTouchTheTravelPlanner() throws IOException {
        String css = resource("/static/css/style.css");

        // 공동 여행계획 화면은 자기 CSS 를 쓰고 여기서는 건드리지 않는다
        assertThat(css)
                .doesNotContain("travel-plan-paper")
                .doesNotContain("travel-plan-day")
                .doesNotContain("travel-plan-line");
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
        User user = new User();
        user.setId("ADMIN".equals(role) ? 2L : 1L);
        user.setUsername(username);
        user.setUserPassword("password");
        user.setUserRole(UserRole.valueOf(role));
        when(userMapper.findById(user.getId())).thenReturn(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());

        return Jsoup.parse(mockMvc.perform(get("/random-travel")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
