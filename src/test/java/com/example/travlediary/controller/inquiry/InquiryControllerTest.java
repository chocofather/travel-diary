package com.example.travlediary.controller.inquiry;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryForm;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.model.InquiryType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.inquiry.InquiryEditConflictException;
import com.example.travlediary.service.inquiry.InquiryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

@WebMvcTest(InquiryController.class)
@Import(SecurityConfig.class)
class InquiryControllerTest {

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
    void guestCannotOpenInquiryPagesButPublicSupportPagesRemainOutsideThisMatcher() throws Exception {
        mockMvc.perform(get("/support/inquiries"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/support/inquiries"));
        mockMvc.perform(get("/support/inquiries/new"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/support/inquiries/10"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/support/inquiries/10/edit"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void memberSeesOnlyServiceProvidedOwnPageWithStablePagination() throws Exception {
        InquiryListItemDto item = listItem();
        when(inquiryService.countMyInquiries(7L)).thenReturn(11L);
        when(inquiryService.getMyInquiries(7L, 10L, 10)).thenReturn(List.of(item));

        mockMvc.perform(get("/support/inquiries").param("page", "2").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/list"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("pageSize", 10))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("답변대기")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/support/inquiries/10")));

        verify(inquiryService).getMyInquiries(7L, 10L, 10);
    }

    @Test
    void invalidPageIsNormalizedAndEmptyStateLinksToCreate() throws Exception {
        when(inquiryService.countMyInquiries(7L)).thenReturn(0L);
        when(inquiryService.getMyInquiries(7L, 0L, 10)).thenReturn(List.of());

        mockMvc.perform(get("/support/inquiries").param("page", "invalid").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "등록한 1:1 문의가 없습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/support/inquiries/new\"")));
    }

    @Test
    void memberCreateUsesPrincipalIgnoresRequestUserAndRequiresCsrf() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .with(user(member()))
                        .param("inquiryType", "ACCOUNT")
                        .param("subject", "로그인 오류")
                        .param("content", "문의 내용")
                        .param("userId", "999")
                        .param("status", "ANSWERED"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/support/inquiries")
                        .with(user(member())).with(csrf())
                        .param("inquiryType", "ACCOUNT")
                        .param("subject", "로그인 오류")
                        .param("content", "문의 내용")
                        .param("userId", "999")
                        .param("status", "ANSWERED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/inquiries"));

        ArgumentCaptor<InquiryForm> captor = ArgumentCaptor.forClass(InquiryForm.class);
        verify(inquiryService).create(captor.capture(), eq(7L));
        assertThat(captor.getValue().getInquiryType()).isEqualTo(InquiryType.ACCOUNT);
        assertThat(InquiryForm.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userId", "status");
    }

    @Test
    void createFormProvidesWhitelistedTypesPlainTextareaAndCsrf() throws Exception {
        mockMvc.perform(get("/support/inquiries/new").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원/계정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여행정보")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("오류/장애")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("quill-editor"))));
    }

    @Test
    void invalidTypeBindingKeepsFormAndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .with(user(member())).with(csrf())
                        .param("inquiryType", "NOT_ALLOWED")
                        .param("subject", "문의")
                        .param("content", "내용"))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/form"))
                .andExpect(model().attributeHasFieldErrors("inquiryForm", "inquiryType"));

        verify(inquiryService, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ownDetailRendersPendingAndAnsweredPlainTextSafely() throws Exception {
        InquiryDetailDto pending = detail(false);
        when(inquiryService.getMyInquiry(10L, 7L)).thenReturn(pending);

        mockMvc.perform(get("/support/inquiries/10").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "아직 답변이 등록되지 않았습니다.")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("script")).noneMatch(
                            element -> element.data().contains("alert(1)"));
                    assertThat(document.select("form[action=/support/inquiries/10/delete]")).hasSize(1);
                    assertThat(document.select("a[href=/support/inquiries/10/edit]")).hasSize(1);
                    assertThat(document.select(".support-navigation-link.is-active[aria-current=page]").text())
                            .isEqualTo("1:1 문의");
                });

        InquiryDetailDto answered = detail(true);
        when(inquiryService.getMyInquiry(11L, 7L)).thenReturn(answered);
        mockMvc.perform(get("/support/inquiries/11").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("form[action=/support/inquiries/11/delete]")).isEmpty();
                    assertThat(document.select("a[href=/support/inquiries/11/edit]")).isEmpty();
                });
    }

    @Test
    void pendingEditFormLoadsExistingValuesAndReusesPlainTextForm() throws Exception {
        InquiryForm form = new InquiryForm();
        form.setInquiryType(InquiryType.TRAVEL_INFO);
        form.setSubject("기존 제목");
        form.setContent("기존\n내용");
        when(inquiryService.getEditableMyInquiry(10L, 7L)).thenReturn(form);

        mockMvc.perform(get("/support/inquiries/10/edit").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/form"))
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attribute("activeInquiryTab", "list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1:1 문의 수정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("기존 제목")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("기존\n내용")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/support/inquiries/10/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("quill-editor"))));
    }

    @Test
    void answeredEditRedirectsToOwnDetailWithSafeMessage() throws Exception {
        when(inquiryService.getEditableMyInquiry(10L, 7L))
                .thenThrow(new InquiryEditConflictException("답변이 완료된 문의는 수정할 수 없습니다."));

        mockMvc.perform(get("/support/inquiries/10/edit").with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/inquiries/10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("inquiryMessage", "답변이 완료된 문의는 수정할 수 없습니다."));
    }

    @Test
    void missingOrOtherUsersEditReturnsApplicationNotFound() throws Exception {
        when(inquiryService.getEditableMyInquiry(99L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/support/inquiries/99/edit").with(user(member())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/support/inquiries/not-a-number/edit").with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingEditUsesPrincipalIgnoresControlFieldsAndRequiresCsrf() throws Exception {
        mockMvc.perform(post("/support/inquiries/10/edit")
                        .with(user(member()))
                        .param("inquiryType", "COMMUNITY")
                        .param("subject", "수정 제목")
                        .param("content", "수정 내용"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/support/inquiries/10/edit")
                        .with(user(member())).with(csrf())
                        .param("inquiryType", "COMMUNITY")
                        .param("subject", "수정 제목")
                        .param("content", "수정 내용")
                        .param("userId", "999")
                        .param("status", "ANSWERED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/inquiries/10"));

        ArgumentCaptor<InquiryForm> captor = ArgumentCaptor.forClass(InquiryForm.class);
        verify(inquiryService).updatePendingMyInquiry(eq(10L), captor.capture(), eq(7L));
        assertThat(captor.getValue().getInquiryType()).isEqualTo(InquiryType.COMMUNITY);
        assertThat(InquiryForm.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userId", "status");
    }

    @Test
    void answerCompletedDuringEditRedirectsWithoutOverwriting() throws Exception {
        org.mockito.Mockito.doThrow(new InquiryEditConflictException(
                        "답변이 완료된 문의는 수정할 수 없습니다."))
                .when(inquiryService).updatePendingMyInquiry(
                        eq(10L), org.mockito.ArgumentMatchers.any(InquiryForm.class), eq(7L));

        mockMvc.perform(post("/support/inquiries/10/edit")
                        .with(user(member())).with(csrf())
                        .param("inquiryType", "ACCOUNT")
                        .param("subject", "늦은 수정")
                        .param("content", "답변 후 제출"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/inquiries/10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("inquiryMessage", "답변이 완료된 문의는 수정할 수 없습니다."));
    }

    @Test
    void editValidationKeepsSubmittedValuesWithoutCallingUpdate() throws Exception {
        mockMvc.perform(post("/support/inquiries/10/edit")
                        .with(user(member())).with(csrf())
                        .param("inquiryType", "NOT_ALLOWED")
                        .param("subject", "수정 제목")
                        .param("content", "수정 내용"))
                .andExpect(status().isOk())
                .andExpect(view().name("support/inquiries/form"))
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attributeHasFieldErrors("inquiryForm", "inquiryType"));

        verify(inquiryService, never()).updatePendingMyInquiry(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherUsersOrMissingDetailReturnsApplicationNotFound() throws Exception {
        when(inquiryService.getMyInquiry(99L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/support/inquiries/99").with(user(member())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/support/inquiries/not-a-number").with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDoesNotBypassOwnershipOnUserInquiryRoute() throws Exception {
        when(inquiryService.getMyInquiry(10L, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/support/inquiries/10").with(user(admin())))
                .andExpect(status().isNotFound());

        verify(inquiryService).getMyInquiry(10L, 99L);
    }

    @Test
    void pendingDeleteUsesPrincipalAndRequiresCsrf() throws Exception {
        mockMvc.perform(post("/support/inquiries/10/delete").with(user(member())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/support/inquiries/10/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/inquiries"));

        verify(inquiryService).deletePendingMyInquiry(10L, 7L);
    }

    private CustomUserDetails member() {
        User user = new User();
        user.setId(7L);
        user.setUsername("member");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
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
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return item;
    }

    private InquiryDetailDto detail(boolean answered) {
        InquiryDetailDto detail = new InquiryDetailDto();
        detail.setId(answered ? 11L : 10L);
        detail.setSubject("로그인 오류 문의");
        detail.setContent("첫 줄\n<script>alert(1)</script>");
        detail.setStatus(answered ? InquiryStatus.ANSWERED : InquiryStatus.PENDING);
        detail.setInquiryType(InquiryType.ACCOUNT);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        if (answered) {
            detail.setAnswerId(20L);
            detail.setAnswerContent("답변\n<img src=x onerror=alert(1)>");
            detail.setAnswerCreatedAt(Timestamp.valueOf("2026-08-12 12:00:00"));
        }
        return detail;
    }
}
