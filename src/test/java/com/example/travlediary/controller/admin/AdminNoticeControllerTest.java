package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.notice.NoticeService;
import com.example.travlediary.service.notice.NoticeValidationException;
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

@WebMvcTest(AdminNoticeController.class)
@Import(SecurityConfig.class)
class AdminNoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanOpenListAndFormWithQuill() throws Exception {
        NoticeListItemDto item = item();
        when(noticeService.getAdminList()).thenReturn(List.of(item));

        mockMvc.perform(get("/admin/notices").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notices/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서비스 점검 안내")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("상단 고정")));

        mockMvc.perform(get("/admin/notices/new").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notices/form"))
                .andExpect(model().attribute("formAction", "/admin/notices"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/quill-editor-init.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/admin-notice-form.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    /** 번역 탭은 한국어를 뺀 네 언어만 그리고, 입력값은 슬롯 이름 그대로 서비스까지 실려 간다. */
    @Test
    void formRendersForeignLanguageTabsAndBindsTheirSlots() throws Exception {
        String body = mockMvc.perform(get("/admin/notices/new").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = org.jsoup.Jsoup.parse(body);

        assertThat(document.select("[data-translation-tab]").eachAttr("data-translation-tab"))
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(document.select("[data-translation-tab]").eachText())
                .containsExactly("영어", "일본어", "간체", "번체");
        assertThat(document.select("[name='translations[0].title']")).isEmpty();
        for (int slot = 1; slot <= 4; slot++) {
            assertThat(document.select("[name='translations[" + slot + "].title']")).hasSize(1);
            assertThat(document.select("[name='translations[" + slot + "].content']")).hasSize(1);
        }
        assertThat(document.select("input[name='translations[1].languageCode']").attr("value"))
                .isEqualTo("en");

        mockMvc.perform(post("/admin/notices")
                        .with(user(admin())).with(csrf())
                        .param("title", "서비스 점검 안내")
                        .param("content", "<p>본문</p>")
                        .param("translations[1].languageCode", "en")
                        .param("translations[1].title", "Maintenance")
                        .param("translations[1].content", "<p>Body</p>"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<NoticeForm> captor = ArgumentCaptor.forClass(NoticeForm.class);
        verify(noticeService).create(captor.capture(), eq(7L));
        assertThat(captor.getValue().getTranslations().get(1).getLanguageCode()).isEqualTo("en");
        assertThat(captor.getValue().getTranslations().get(1).getTitle()).isEqualTo("Maintenance");
        assertThat(captor.getValue().getTranslations().get(1).getContent()).isEqualTo("<p>Body</p>");
    }

    @Test
    void adminCreateUsesPrincipalIdAndRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/notices")
                        .with(user(admin()))
                        .param("title", "서비스 점검 안내")
                        .param("content", "<p>본문</p>")
                        .param("pinned", "true"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/notices")
                        .with(user(admin())).with(csrf())
                        .param("title", "서비스 점검 안내")
                        .param("content", "<p>본문</p>")
                        .param("pinned", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notices"));

        ArgumentCaptor<NoticeForm> captor = ArgumentCaptor.forClass(NoticeForm.class);
        verify(noticeService).create(captor.capture(), eq(7L));
        assertThat(captor.getValue().isPinned()).isTrue();
    }

    @Test
    void editKeepsSubmittedInputWhenValidationFails() throws Exception {
        doThrow(new NoticeValidationException("content", "본문을 입력해 주세요."))
                .when(noticeService).update(eq(10L), any(NoticeForm.class));

        mockMvc.perform(post("/admin/notices/10/edit")
                        .with(user(admin())).with(csrf())
                        .param("title", "수정 제목")
                        .param("content", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notices/form"))
                .andExpect(model().attributeHasFieldErrors("noticeForm", "content"))
                .andExpect(model().attribute("formAction", "/admin/notices/10/edit"));
    }

    @Test
    void deleteIsPostOnlyAndRequiresAdminWithCsrf() throws Exception {
        mockMvc.perform(get("/admin/notices/10/delete").with(user(admin())))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/admin/notices/10/delete").with(user(admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/notices/10/delete")
                        .with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notices"));

        verify(noticeService).delete(10L);
    }

    @Test
    void regularUserCannotAccessAdminNotices() throws Exception {
        mockMvc.perform(get("/admin/notices").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }

    private NoticeListItemDto item() {
        NoticeListItemDto item = new NoticeListItemDto();
        item.setId(10L);
        item.setTitle("서비스 점검 안내");
        item.setPinned(true);
        item.setViews(12);
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        item.setUpdatedAt(Timestamp.valueOf("2026-08-12 11:00:00"));
        return item;
    }
}
