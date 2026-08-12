package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.InquiryAnswerForm;
import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.model.InquiryType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.inquiry.InquiryService;
import com.example.travlediary.service.inquiry.InquiryValidationException;
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

@WebMvcTest(AdminInquiryController.class)
@Import(SecurityConfig.class)
class AdminInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryService inquiryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminListSupportsSafeStatusFilterAndPagination() throws Exception {
        InquiryListItemDto item = listItem();
        when(inquiryService.countAdminInquiries(InquiryStatus.PENDING)).thenReturn(21L);
        when(inquiryService.getAdminInquiries(InquiryStatus.PENDING, 20L, 20))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/admin/inquiries")
                        .param("status", "PENDING")
                        .param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inquiries/list"))
                .andExpect(model().attribute("currentStatus", "PENDING"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("pageSize", 20))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member-nick")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "status=PENDING")));

        verify(inquiryService).getAdminInquiries(InquiryStatus.PENDING, 20L, 20);
    }

    @Test
    void invalidStatusFallsBackToAllWithoutPassingRawValue() throws Exception {
        when(inquiryService.countAdminInquiries(null)).thenReturn(0L);
        when(inquiryService.getAdminInquiries(null, 0L, 20)).thenReturn(List.of());

        mockMvc.perform(get("/admin/inquiries")
                        .param("status", "DROP TABLE")
                        .param("page", "bad")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentStatus", "ALL"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("등록된 문의가 없습니다.")));
    }

    @Test
    void adminDetailLoadsExistingAnswerForEditAsEscapedTextarea() throws Exception {
        InquiryDetailDto detail = detail(true);
        when(inquiryService.getAdminInquiry(10L)).thenReturn(detail);

        mockMvc.perform(get("/admin/inquiries/10").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inquiries/detail"))
                .andExpect(model().attribute("answerMode", "edit"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("답변 수정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void answerUsesAdminPrincipalAndRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/inquiries/10/answer")
                        .with(user(admin()))
                        .param("content", "관리자 답변"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/inquiries/10/answer")
                        .with(user(admin())).with(csrf())
                        .param("content", "관리자 답변"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inquiries/10"));

        ArgumentCaptor<InquiryAnswerForm> captor = ArgumentCaptor.forClass(InquiryAnswerForm.class);
        verify(inquiryService).saveAnswer(eq(10L), captor.capture(), eq(99L));
        assertThat(captor.getValue().getContent()).isEqualTo("관리자 답변");
    }

    @Test
    void answerValidationKeepsSubmittedTextAndDetail() throws Exception {
        InquiryDetailDto detail = detail(false);
        when(inquiryService.getAdminInquiry(10L)).thenReturn(detail);
        doThrow(new InquiryValidationException("content", "답변 내용을 입력해 주세요."))
                .when(inquiryService).saveAnswer(eq(10L), any(InquiryAnswerForm.class), eq(99L));

        mockMvc.perform(post("/admin/inquiries/10/answer")
                        .with(user(admin())).with(csrf())
                        .param("content", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inquiries/detail"))
                .andExpect(model().attributeHasFieldErrors("answerForm", "content"));
    }

    @Test
    void regularUserCannotAccessAdminInquiryRoutes() throws Exception {
        mockMvc.perform(get("/admin/inquiries").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/inquiries/10").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(99L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }

    private InquiryListItemDto listItem() {
        InquiryListItemDto item = new InquiryListItemDto();
        item.setId(10L);
        item.setSubject("로그인 오류 문의");
        item.setStatus(InquiryStatus.PENDING);
        item.setInquiryType(InquiryType.ACCOUNT);
        item.setUserDisplayName("member-nick");
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return item;
    }

    private InquiryDetailDto detail(boolean answered) {
        InquiryDetailDto detail = new InquiryDetailDto();
        detail.setId(10L);
        detail.setSubject("로그인 오류 문의");
        detail.setContent("문의 내용");
        detail.setStatus(answered ? InquiryStatus.ANSWERED : InquiryStatus.PENDING);
        detail.setInquiryType(InquiryType.ACCOUNT);
        detail.setUserDisplayName("member-nick");
        detail.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        if (answered) {
            detail.setAnswerId(20L);
            detail.setAnswerContent("답변\n<img src=x onerror=alert(1)>");
            detail.setAnswerCreatedAt(Timestamp.valueOf("2026-08-12 12:00:00"));
        }
        return detail;
    }
}
