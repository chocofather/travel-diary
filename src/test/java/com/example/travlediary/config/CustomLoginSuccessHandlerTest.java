package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomLoginSuccessHandlerTest {

    @Mock
    private UserMapper userMapper;

    private CustomLoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomLoginSuccessHandler(userMapper);
    }

    @Test
    void adminGeneralLoginUsesNormalSiteRedirectInsteadOfForcedAdminHome() throws Exception {
        when(userMapper.findByUsername("admin")).thenReturn(user(99L, "admin", UserRole.ADMIN));
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("admin", "ROLE_ADMIN"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(99L);
    }

    @Test
    void directAdminLoginWithoutOriginalRequestFallsBackToGeneralHome() throws Exception {
        when(userMapper.findByUsername("admin")).thenReturn(user(99L, "admin", UserRole.ADMIN));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("admin", "ROLE_ADMIN"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void adminReturnsToSameOriginSavedAdminRequestIncludingQuery() throws Exception {
        when(userMapper.findByUsername("admin")).thenReturn(user(99L, "admin", UserRole.ADMIN));
        MockHttpSession session = saveRequest("/admin/inquiries", "status=PENDING&page=2");
        MockHttpServletRequest request = requestWithRedirect("/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("admin", "ROLE_ADMIN"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/admin/inquiries?status=PENDING&page=2");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserCannotFollowSavedAdminRequestAndFallsBackToHome() throws Exception {
        when(userMapper.findByUsername("member")).thenReturn(user(7L, "member", UserRole.USER));
        MockHttpSession session = saveRequest("/admin", null);
        MockHttpServletRequest request = requestWithRedirect("/admin");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("member", "ROLE_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserKeepsValidatedInternalRedirect() throws Exception {
        when(userMapper.findByUsername("member")).thenReturn(user(7L, "member", UserRole.USER));
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("member", "ROLE_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void externalProtocolRelativeAndMalformedRedirectsFallBackToHome() throws Exception {
        when(userMapper.findByUsername("member")).thenReturn(user(7L, "member", UserRole.USER));

        assertRedirectFallsBack("https://evil.example/path");
        assertRedirectFallsBack("//evil.example/path");
        assertRedirectFallsBack("/\\evil.example/path");
        assertRedirectFallsBack("/%2f%2fevil.example/path");
        assertRedirectFallsBack("/%5cevil.example/path");
        assertRedirectFallsBack("/travel-info%0d%0aLocation:https://evil.example");
        assertRedirectFallsBack("not-an-internal-path");
    }

    @Test
    void missingRedirectFallsBackToHome() throws Exception {
        when(userMapper.findByUsername("member")).thenReturn(user(7L, "member", UserRole.USER));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("member", "ROLE_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private void assertRedirectFallsBack(String redirect) throws Exception {
        MockHttpServletRequest request = requestWithRedirect(redirect);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication("member", "ROLE_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private MockHttpServletRequest requestWithRedirect(String redirect) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", redirect);
        return request;
    }

    private MockHttpSession saveRequest(String requestUri, String query) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest original = new MockHttpServletRequest("GET", requestUri);
        original.setScheme("http");
        original.setServerName("localhost");
        original.setServerPort(80);
        original.setQueryString(query);
        original.setSession(session);
        new HttpSessionRequestCache().saveRequest(original, new MockHttpServletResponse());
        return session;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String authority) {
        return new UsernamePasswordAuthenticationToken(
                username, "password", List.of(new SimpleGrantedAuthority(authority)));
    }

    private User user(Long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserRole(role);
        return user;
    }
}
