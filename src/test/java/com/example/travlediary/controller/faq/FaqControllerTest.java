package com.example.travlediary.controller.faq;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.faq.FaqService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FaqController.class)
@Import(SecurityConfig.class)
class FaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaqService faqService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenFaqListAsNativeAccordionWithEscapedPlainText() throws Exception {
        FaqListItemDto faq = item("회원/계정");
        when(faqService.getPublicList()).thenReturn(List.of(faq));

        mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk())
                .andExpect(view().name("support/faq"))
                .andExpect(model().attribute("faqs", List.of(faq)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("자주 묻는 질문")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("details.support-faq-item")).hasSize(1);
                    assertThat(document.select("details.support-faq-item > summary")).hasSize(1);
                    assertThat(document.select(".support-faq-answer p").text())
                            .contains("<script>alert(1)</script>");
                });
    }

    /** 공개 화면은 쿠키 locale 로 정한 요청 언어를 서비스에 그대로 넘긴다. */
    @Test
    void requestedLanguageFromTheLocaleCookieReachesTheService() throws Exception {
        List<FaqListItemDto> faqs = List.of(item("회원/계정"));
        when(faqService.getPublicList()).thenReturn(faqs);

        mockMvc.perform(get("/support/faq")
                        .cookie(new jakarta.servlet.http.Cookie(
                                TravelDiaryLocaleResolver.COOKIE_NAME, "en")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk());

        verify(faqService).localizePublicList(faqs, SupportedLanguage.ENGLISH);
        // locale 쿠키가 없으면 한국어로 본다
        verify(faqService).localizePublicList(faqs, SupportedLanguage.KOREAN);
    }

    /**
     * 뱃지 클래스는 서버가 정해 준 값 그대로 찍는다.
     * 화면에 보이는 카테고리 이름(언어에 따라 바뀜)으로 스타일을 고르지 않는다.
     */
    @Test
    void categoryBadgeClassesComeFromTheServerAndNotFromTheDisplayedName() throws Exception {
        List<FaqListItemDto> items = List.of(
                item("회원/계정", "is-account"),
                item("여행정보", "is-travel"),
                item("커뮤니티", "is-community"),
                item("서비스 이용", "is-service"),
                item("기타", "is-etc"),
                item("새 카테고리", "is-default")
        );
        when(faqService.getPublicList()).thenReturn(items);

        mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".support-faq-category.is-account").text()).isEqualTo("회원/계정");
                    assertThat(document.select(".support-faq-category.is-travel").text()).isEqualTo("여행정보");
                    assertThat(document.select(".support-faq-category.is-community").text()).isEqualTo("커뮤니티");
                    assertThat(document.select(".support-faq-category.is-service").text()).isEqualTo("서비스 이용");
                    assertThat(document.select(".support-faq-category.is-etc").text()).isEqualTo("기타");
                    assertThat(document.select(".support-faq-category.is-default").text()).isEqualTo("새 카테고리");
                    assertThat(document.select(".support-navigation-link.is-active[aria-current=page]").text())
                            .isEqualTo("자주 묻는 질문");
                });
    }

    /** 고정 UI 문구는 요청 언어를 따르고, DB 콘텐츠(질문·답변·카테고리명)는 서비스가 준 값 그대로다. */
    @Test
    void staticLabelsFollowTheLocaleWhileContentComesFromTheService() throws Exception {
        when(faqService.getPublicList()).thenReturn(List.of(item("회원/계정", "is-account")));

        var korean = Jsoup.parse(mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(korean.select("#support-faq-title").text()).isEqualTo("자주 묻는 질문");
        assertThat(korean.select(".support-navigation-title").text()).isEqualTo("고객센터");

        var english = Jsoup.parse(mockMvc.perform(get("/support/faq")
                        .cookie(new jakarta.servlet.http.Cookie(
                                TravelDiaryLocaleResolver.COOKIE_NAME, "en")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(english.select("#support-faq-title").text()).isEqualTo("FAQ");
        assertThat(english.select(".support-navigation-title").text()).isEqualTo("Support");
        // 카테고리명·질문·답변은 서비스가 지역화한 값을 그대로 쓴다
        assertThat(english.select(".support-faq-category").text()).isEqualTo("회원/계정");
    }

    /** 이름이 번역돼도 같은 뱃지가 유지된다. */
    @Test
    void translatedCategoryNamesKeepTheSameBadgeClass() throws Exception {
        when(faqService.getPublicList()).thenReturn(List.of(
                item("Account", "is-account"), item("アカウント", "is-account")));

        mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".support-faq-category.is-account").eachText())
                            .containsExactly("Account", "アカウント");
                    assertThat(document.select(".support-faq-category.is-default")).isEmpty();
                });
    }

    @Test
    void emptyVisibleListRendersHelpfulState() throws Exception {
        when(faqService.getPublicList()).thenReturn(List.of());

        mockMvc.perform(get("/support/faq"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "등록된 자주 묻는 질문이 없습니다.")));
    }

    @Test
    void faqHasNoPublicDetailRoute() throws Exception {
        mockMvc.perform(get("/support/faq/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/support/faq/1"));
    }

    private FaqListItemDto item(String categoryName) {
        return item(categoryName, "is-account");
    }

    private FaqListItemDto item(String categoryName, String categoryBadge) {
        FaqListItemDto item = new FaqListItemDto();
        item.setId(1L);
        item.setCategoryId(3L);
        item.setCategoryName(categoryName);
        item.setCategoryBadge(categoryBadge);
        item.setQuestion("회원 탈퇴는 어떻게 하나요?");
        item.setAnswer("첫 줄\n<script>alert(1)</script>");
        item.setOrderIndex(1L);
        item.setVisible(true);
        return item;
    }
}
