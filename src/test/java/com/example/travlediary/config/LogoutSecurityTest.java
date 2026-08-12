package com.example.travlediary.config;

import com.example.travlediary.controller.notice.NoticeController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.notice.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
@Import({SecurityConfig.class, CustomLogoutSuccessHandler.class})
class LogoutSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void authenticatedPostLogoutWithCsrfKeepsValidatedPublicPage() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf())
                        .param("redirect", "/support/notices"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/notices"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void publicDetailFaqAndFilteredListRemainAfterLogout() throws Exception {
        assertPublicLogoutRedirect("/destinations/25", "/destinations/25");
        assertPublicLogoutRedirect("/support/notices/10", "/support/notices/10");
        assertPublicLogoutRedirect("/support/faq", "/support/faq");
        assertPublicLogoutRedirect(
                "/travel-info?scope=DOMESTIC&sort=views",
                "/travel-info?scope=DOMESTIC&sort=views");
        assertPublicLogoutRedirect("/post/12", "/post/12");
    }

    @Test
    void logoutDoesNotCarryPreviousAccountsPrivateOrExternalRedirect() throws Exception {
        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "/support/inquiries/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "/admin/inquiries"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "//evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", "javascript:alert(1)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());
    }

    private void assertPublicLogoutRedirect(String redirect, String expected) throws Exception {
        mockMvc.perform(post("/logout")
                        .session(authenticatedSession())
                        .with(csrf())
                        .param("redirect", redirect))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expected))
                .andExpect(unauthenticated());
    }

    @Test
    void postLogoutWithoutCsrfIsRejectedAndSessionRemainsAuthenticated() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void getLogoutIsNotConfiguredAsActualLogoutRequest() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().isNotFound());

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    private MockHttpSession authenticatedSession() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "member", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
