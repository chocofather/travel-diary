package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginFailureHandlerTest {

    @Test
    void oauthFailureReturnsToLoginWithoutPersistingTechnicalException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2LoginFailureHandler().onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=true");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void withdrawalOauthFailureConsumesIntentAndRestoresExistingTravelDiaryLogin()
            throws Exception {
        TravelDiaryAuthenticationRestorer restorer =
                mock(TravelDiaryAuthenticationRestorer.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        PendingSocialWithdrawal pending = new PendingSocialWithdrawal(
                "flow-id",
                7L,
                SocialProvider.GOOGLE,
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(600));
        request.getSession().setAttribute("userId", 7L);
        request.getSession().setAttribute(
                PendingSocialWithdrawal.SESSION_ATTRIBUTE, pending);
        when(restorer.restore(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2LoginFailureHandler(restorer).onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(request.getSession().getAttribute(
                PendingSocialWithdrawal.SESSION_ATTRIBUTE)).isNull();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/mypage/account?socialWithdrawalError=true");
        verify(restorer).restore(request, response, 7L);
    }
}
