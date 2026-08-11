package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.faq.FaqService;
import com.example.travlediary.service.faq.FaqValidationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminFaqController.class)
@Import(SecurityConfig.class)
class AdminFaqControllerTest {

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
    void adminCanOpenListAndCategoryBackedForm() throws Exception {
        when(faqService.getAdminList()).thenReturn(List.of(item()));
        when(faqService.getCategories()).thenReturn(List.of(category()));

        mockMvc.perform(get("/admin/faqs").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faqs/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원 탈퇴는 어떻게 하나요?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")));

        mockMvc.perform(get("/admin/faqs/new").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faqs/form"))
                .andExpect(model().attribute("formAction", "/admin/faqs"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("quill-editor"))));
    }

    @Test
    void createUsesPrincipalIdAndRequiresCsrf() throws Exception {
        when(faqService.getCategories()).thenReturn(List.of(category()));

        mockMvc.perform(createRequest().with(user(admin())))
                .andExpect(status().isForbidden());

        mockMvc.perform(createRequest().with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faqs"));

        ArgumentCaptor<FaqForm> captor = ArgumentCaptor.forClass(FaqForm.class);
        verify(faqService).create(captor.capture(), eq(7L));
        assertThat(captor.getValue().getCategoryId()).isEqualTo(3L);
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(2L);
        assertThat(captor.getValue().isVisible()).isTrue();
    }

    @Test
    void validationFailureKeepsInputAndReloadsCategories() throws Exception {
        when(faqService.getCategories()).thenReturn(List.of(category()));
        doThrow(new FaqValidationException("answer", "답변을 입력해 주세요."))
                .when(faqService).update(eq(10L), any(FaqForm.class));

        mockMvc.perform(post("/admin/faqs/10/edit")
                        .with(user(admin())).with(csrf())
                        .param("categoryId", "3")
                        .param("question", "수정 질문")
                        .param("answer", "")
                        .param("orderIndex", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/faqs/form"))
                .andExpect(model().attributeHasFieldErrors("faqForm", "answer"))
                .andExpect(model().attribute("formAction", "/admin/faqs/10/edit"))
                .andExpect(model().attribute("categories", List.of(category())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<option value=\"3\" selected=\"selected\">회원/계정</option>")));
    }

    @Test
    void editMutationAlsoRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/faqs/10/edit")
                        .with(user(admin()))
                        .param("categoryId", "3")
                        .param("question", "수정 질문")
                        .param("answer", "수정 답변")
                        .param("orderIndex", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIsPostOnlyAndRequiresAdminWithCsrf() throws Exception {
        mockMvc.perform(get("/admin/faqs/10/delete").with(user(admin())))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/admin/faqs/10/delete").with(user(admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/faqs/10/delete")
                        .with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faqs"));

        verify(faqService).delete(10L);
    }

    @Test
    void regularUserCannotAccessAdminFaqs() throws Exception {
        mockMvc.perform(get("/admin/faqs").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest() {
        return post("/admin/faqs")
                .param("categoryId", "3")
                .param("question", "회원 탈퇴는 어떻게 하나요?")
                .param("answer", "회원정보 수정에서 탈퇴할 수 있습니다.")
                .param("orderIndex", "2")
                .param("visible", "true");
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }

    private FaqCategory category() {
        FaqCategory category = new FaqCategory();
        category.setId(3L);
        category.setCategoryName("회원/계정");
        return category;
    }

    private FaqListItemDto item() {
        FaqListItemDto item = new FaqListItemDto();
        item.setId(10L);
        item.setQuestion("회원 탈퇴는 어떻게 하나요?");
        item.setAnswer("회원정보 수정에서 탈퇴할 수 있습니다.");
        item.setCategoryId(3L);
        item.setCategoryName("회원/계정");
        item.setOrderIndex(2L);
        item.setVisible(true);
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        item.setUpdatedAt(Timestamp.valueOf("2026-08-12 11:00:00"));
        return item;
    }
}
