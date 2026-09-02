package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void restrictedMemberGoesToTheRestrictedPageBeforeAnySavedRedirect() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.RESTRICTED);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/restricted");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
        verify(userMapper, never()).findByUsername(anyString());
    }

    @Test
    void activeMemberKeepsTheExistingRedirectBehaviour() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void adminGeneralLoginUsesNormalSiteRedirectInsteadOfForcedAdminHome() throws Exception {
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(99L);
    }

    @Test
    void directAdminLoginWithoutOriginalRequestFallsBackToGeneralHome() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void adminReturnsToSameOriginSavedAdminRequestIncludingQuery() throws Exception {
        MockHttpSession session = saveRequest("/admin/inquiries", "status=PENDING&page=2");
        MockHttpServletRequest request = requestWithRedirect("/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/admin/inquiries?status=PENDING&page=2");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserCannotFollowSavedAdminRequestAndFallsBackToHome() throws Exception {
        MockHttpSession session = saveRequest("/admin", null);
        MockHttpServletRequest request = requestWithRedirect("/admin");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserKeepsValidatedInternalRedirect() throws Exception {
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void externalProtocolRelativeAndMalformedRedirectsFallBackToHome() throws Exception {
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
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private void assertRedirectFallsBack(String redirect) throws Exception {
        MockHttpServletRequest request = requestWithRedirect(redirect);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

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

    private UsernamePasswordAuthenticationToken authentication(Long id,
                                                               String username,
                                                               UserRole role) {
        CustomUserDetails userDetails = new CustomUserDetails(user(id, username, role));
        return new UsernamePasswordAuthenticationToken(
                userDetails, "password", userDetails.getAuthorities());
    }

    private User user(Long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserRole(role);
        return user;
    }
}
