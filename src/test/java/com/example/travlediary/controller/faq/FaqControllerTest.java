package com.example.travlediary.controller.faq;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
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

    @Test
    void categoryNamesRenderStablePresentationClassesWithNeutralFallback() throws Exception {
        List<FaqListItemDto> items = List.of(
                item("회원/계정"),
                item("여행정보"),
                item("커뮤니티"),
                item("서비스 이용"),
                item("기타"),
                item("새 카테고리")
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
        FaqListItemDto item = new FaqListItemDto();
        item.setId(1L);
        item.setCategoryName(categoryName);
        item.setQuestion("회원 탈퇴는 어떻게 하나요?");
        item.setAnswer("첫 줄\n<script>alert(1)</script>");
        item.setOrderIndex(1L);
        item.setVisible(true);
        return item;
    }
}
