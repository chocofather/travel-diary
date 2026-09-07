package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.FaqCategoryForm;
import com.example.travlediary.dto.FaqCategoryListItemDto;
import com.example.travlediary.dto.FaqCategoryTranslationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.faq.FaqCategoryInUseException;
import com.example.travlediary.service.faq.FaqCategoryService;
import com.example.travlediary.service.faq.FaqValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 관리자 FAQ 카테고리 화면. 목록·등록·수정·삭제와 번역 탭 바인딩을 본다.
 */
@WebMvcTest(AdminFaqCategoryController.class)
@Import(SecurityConfig.class)
class AdminFaqCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private FaqCategoryService faqCategoryService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void listShowsEveryCategoryWithItsFaqUsage() throws Exception {
        when(faqCategoryService.getAdminList()).thenReturn(List.of(item(3L, "회원/계정", 4)));

        mockMvc.perform(get("/admin/faq-categories").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faq-categories/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("4건")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/admin/faq-categories/edit/3")));
    }

    @Test
    void createFormRendersForeignLanguageTabsAndBindsTheirSlots() throws Exception {
        String body = mockMvc.perform(get("/admin/faq-categories/create").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faq-categories/form"))
                .andExpect(model().attribute("formAction", "/admin/faq-categories"))
                .andReturn().getResponse().getContentAsString();
        var document = org.jsoup.Jsoup.parse(body);

        assertThat(document.select("[data-translation-tab]").eachAttr("data-translation-tab"))
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(document.select("[data-translation-tab]").eachText())
                .containsExactly("영어", "일본어", "간체", "번체");
        assertThat(document.select("[name='translations[0].categoryName']")).isEmpty();
        for (int slot = 1; slot <= 4; slot++) {
            assertThat(document.select("[name='translations[" + slot + "].categoryName']"))
                    .hasSize(1);
        }
        assertThat(document.select("input[name='translations[1].languageCode']").attr("value"))
                .isEqualTo("en");
        // 이름 한 칸뿐이라 편집기는 쓰지 않는다
        assertThat(body).doesNotContain("quill");

        mockMvc.perform(post("/admin/faq-categories").with(user(admin())).with(csrf())
                        .param("categoryName", "회원/계정")
                        .param("translations[1].languageCode", "en")
                        .param("translations[1].categoryName", "Account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faq-categories"));

        ArgumentCaptor<FaqCategoryForm> captor = ArgumentCaptor.forClass(FaqCategoryForm.class);
        verify(faqCategoryService).create(captor.capture());
        assertThat(captor.getValue().getCategoryName()).isEqualTo("회원/계정");
        assertThat(captor.getValue().getTranslations())
                .extracting(FaqCategoryTranslationForm::getLanguageCode,
                        FaqCategoryTranslationForm::getCategoryName)
                .contains(tuple("en", "Account"));
    }

    @Test
    void editFormLoadsStoredValuesAndUpdatePostsToTheSameRoute() throws Exception {
        FaqCategoryForm form = new FaqCategoryForm();
        form.setCategoryName("회원/계정");
        form.setTranslations(List.of(
                new FaqCategoryTranslationForm("ko", ""),
                new FaqCategoryTranslationForm("en", "Account"),
                new FaqCategoryTranslationForm("ja", ""),
                new FaqCategoryTranslationForm("zh-CN", ""),
                new FaqCategoryTranslationForm("zh-TW", "帳戶")));
        when(faqCategoryService.getForm(3L)).thenReturn(form);

        String body = mockMvc.perform(get("/admin/faq-categories/edit/3").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("formAction", "/admin/faq-categories/edit/3"))
                .andReturn().getResponse().getContentAsString();
        var document = org.jsoup.Jsoup.parse(body);

        assertThat(document.select("#faq-category-name").attr("value")).isEqualTo("회원/계정");
        assertThat(document.select("[data-translation-name='en']").attr("value"))
                .isEqualTo("Account");
        assertThat(document.select("[data-translation-name='zh-TW']").attr("value"))
                .isEqualTo("帳戶");
        assertThat(document.select("[data-translation-name='ja']").attr("value")).isEmpty();

        mockMvc.perform(post("/admin/faq-categories/edit/3").with(user(admin())).with(csrf())
                        .param("categoryName", "회원/계정 관리"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faq-categories"));

        verify(faqCategoryService).update(eq(3L), any(FaqCategoryForm.class));
    }

    @Test
    void aDuplicateNameIsShownOnTheNameFieldWithoutRedirect() throws Exception {
        doThrow(new FaqValidationException("categoryName", "이미 등록된 카테고리명입니다."))
                .when(faqCategoryService).create(any(FaqCategoryForm.class));

        mockMvc.perform(post("/admin/faq-categories").with(user(admin())).with(csrf())
                        .param("categoryName", "회원/계정"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faq-categories/form"))
                .andExpect(model().attributeHasFieldErrors("faqCategoryForm", "categoryName"));
    }

    @Test
    void deleteIsPostOnlyAndShowsTheReasonWhenTheCategoryIsInUse() throws Exception {
        mockMvc.perform(get("/admin/faq-categories/3/delete").with(user(admin())))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/admin/faq-categories/3/delete").with(user(admin())))
                .andExpect(status().isForbidden());
        verify(faqCategoryService, never()).delete(anyLong());

        mockMvc.perform(post("/admin/faq-categories/3/delete").with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faq-categories"))
                .andExpect(flash().attributeCount(0));
        verify(faqCategoryService).delete(3L);

        doThrow(new FaqCategoryInUseException(2)).when(faqCategoryService).delete(4L);
        mockMvc.perform(post("/admin/faq-categories/4/delete").with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error",
                        "현재 2개의 FAQ에서 사용 중이라 삭제할 수 없습니다."));
    }

    private FaqCategoryListItemDto item(Long id, String categoryName, int faqCount) {
        FaqCategoryListItemDto item = new FaqCategoryListItemDto();
        item.setId(id);
        item.setCategoryName(categoryName);
        item.setFaqCount(faqCount);
        return item;
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }
}
