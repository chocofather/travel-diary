package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 비회원 체험 경로만 열고, 회원 다이어리 경로의 인증은 그대로인지 본다.
 *
 * <p>체험 화면 하나를 공개하면서 회원용 저장 endpoint 까지 같이 열리면
 * 비회원이 남의 다이어리에 글을 쓸 수 있게 된다. 그 경계를 여기에서 고정한다.
 */
@WebMvcTest(controllers = GuestDiaryDemoController.class)
@Import(SecurityConfig.class)
class GuestDiaryDemoAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    // 꾸미기 목록은 resources 의 manifest 를 읽는 카탈로그다. DB 를 타지 않는다.
    @MockitoBean
    private com.example.travlediary.service.diary.DiaryStickerCatalog diaryStickerCatalog;
    @MockitoBean
    private com.example.travlediary.service.diary.DiaryNoteCatalog diaryNoteCatalog;
    @MockitoBean
    private com.example.travlediary.service.diary.DiaryLabelFontCatalog diaryLabelFontCatalog;

    /** 1) 체험 시작 화면과 체험 편집 화면은 로그인 없이 열린다. */
    @Test
    void theGuestDemoPagesAreOpenToVisitors() throws Exception {
        mockMvc.perform(get("/diaries/demo"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo"));
        mockMvc.perform(get("/diaries/demo/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-new"));
        mockMvc.perform(get("/diaries/demo/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-edit"));
        mockMvc.perform(get("/diaries/demo/cover"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-cover"));
    }

    /**
     * 1) 체험 화면은 뒤에 물음표가 붙어도 로그인 없이 열린다.
     *
     * <p>이 규칙을 정하는 matcher 는 경로만이 아니라 물음표 뒤까지 붙인 문자열을 본다.
     * 그래서 쿼리를 허용하지 않으면 덜 채워진 체험 여행일기를 보완하러 가는
     * {@code /diaries/demo/new?mode=complete} 가 회원 경로로 떨어져 로그인 화면으로 끌려간다.
     */
    @Test
    void theGuestDemoPagesStayOpenWhenTheUrlCarriesAQuery() throws Exception {
        mockMvc.perform(get("/diaries/demo/new?mode=complete&returnTo=edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-new"));
        mockMvc.perform(get("/diaries/demo/new?mode=complete&returnTo=import"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-new"));
        mockMvc.perform(get("/diaries/demo/new?mode=complete&returnTo=shelf"))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/demo-new"));
    }

    /**
     * 2) 회원 다이어리 경로는 그대로 로그인이 필요하다.
     * 체험 화면을 열었다고 목록이나 저장 endpoint 가 함께 열리지 않는다.
     */
    @Test
    void theMemberDiaryPathsStillRequireLogin() throws Exception {
        for (String path : new String[]{
                "/diaries", "/diaries/new", "/diaries/calendar",
                "/diaries/10", "/diaries/cover-designs", "/diaries/cover-designs/5/edit",
                // 체험 쿼리를 그대로 붙여도 회원 경로가 열리지 않는다.
                "/diaries/new?mode=complete&returnTo=edit",
                "/diaries/demo/new/evil"}) {
            String redirect = mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();
            assertThat(redirect).as(path).startsWith("/login");
        }
    }

    /** 2) DB 에 쓰는 POST 는 비회원에게 열려 있지 않다. */
    @Test
    void theMemberDiaryWriteEndpointsAreNotOpened() throws Exception {
        for (String path : new String[]{
                "/diaries", "/diaries/10/pages", "/diaries/10/update",
                "/diaries/10/pages/20/content",
                "/diaries/10/pages/20/elements/sticker",
                "/diaries/cover-designs", "/diaries/cover-designs/5/update",
                "/diaries/cover-designs/5/elements/sticker"}) {
            // CSRF 토큰 없이 막히든 로그인으로 되돌리든, 비회원 요청이 통과하지만 않으면 된다.
            int rejected = mockMvc.perform(post(path)).andReturn().getResponse().getStatus();
            assertThat(rejected).as(path).isGreaterThanOrEqualTo(300);
        }
    }

    /**
     * 8) 체험 여행일기 가져오기 자리는 로그인해야 열린다.
     * 로그인 화면으로 보내면서 원래 가려던 주소를 함께 실어 준다.
     * (그 값이 인증 뒤 복귀의 재료가 된다)
     */
    @Test
    void theImportPageRequiresLoginAndRemembersWhereWeWereGoing() throws Exception {
        String redirect = mockMvc.perform(get("/diaries/import"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirect).isEqualTo("/login?redirect=/diaries/import");
    }

    /** 체험 경로는 GET 화면 두 개뿐이다. 저장용 POST 를 새로 열지 않았다. */
    @Test
    void theGuestDemoPathsHaveNoWriteEndpoint() throws Exception {
        for (String path : new String[]{
                "/diaries/demo", "/diaries/demo/new",
                "/diaries/demo/edit", "/diaries/demo/cover"}) {
            assertThat(mockMvc.perform(post(path)).andReturn().getResponse().getStatus())
                    .as(path).isGreaterThanOrEqualTo(300);
        }
    }
}
