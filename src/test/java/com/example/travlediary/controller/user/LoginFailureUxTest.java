package com.example.travlediary.controller.user;

import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.security.LoginThrottleStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginFailureUxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private LoginThrottle loginThrottle;

    @Test
    void thirdFailureRestoresOnlyUsernameAndShowsTwoAttemptsRemaining() throws Exception {
        RenderedPage rendered = render(new LoginFormState("member", 3, null));
        Document page = rendered.document();
        var inlineMessage = page.selectFirst(
                ".login-password-control + #loginFailureInline");

        assertThat(page.selectFirst("#username").val()).isEqualTo("member");
        assertThat(page.selectFirst("#loginPassword").hasAttr("value")).isFalse();
        assertThat(page.selectFirst("#loginFailureFeedback")).isNull();
        assertThat(inlineMessage).isNotNull();
        assertThat(inlineMessage.text())
                .contains("아이디 또는 비밀번호를 확인해주세요.")
                .contains("로그인 실패 3회 · 2회 더 실패하면 잠시 제한됩니다.");
        assertThat(inlineMessage.hasClass("login-inline-message--warning")).isTrue();
        assertThat(page.selectFirst(".login-submit").hasAttr("disabled")).isFalse();
        assertThat(rendered.session().getAttribute(LoginFormState.SESSION_ATTRIBUTE))
                .isNull();
    }

    @Test
    void firstTwoFailuresShowOnlyTheCompactFailureCountBelowPassword() throws Exception {
        Document page = render(new LoginFormState("member", 2, null)).document();
        var inlineMessage = page.selectFirst("#loginFailureInline");

        assertThat(inlineMessage).isNotNull();
        assertThat(inlineMessage.selectFirst(".login-inline-message__detail").text())
                .isEqualTo("로그인 실패 2회");
        assertThat(inlineMessage.hasClass("login-inline-message--warning")).isFalse();
    }

    @Test
    void fourthFailureShowsOneAttemptUntilTheTenSecondLimit() throws Exception {
        Document page = render(new LoginFormState("missing-account", 4, null))
                .document();

        assertThat(page.selectFirst("#loginFailureInline").text())
                .contains("로그인 실패 4회 · 한 번 더 실패하면 10초 동안 제한됩니다.");
    }

    @Test
    void activeLimitDisablesLoginAndRendersServerRemainingTimeForCountdown() throws Exception {
        LoginFormState state = new LoginFormState(
                "member", 6, Instant.now().plusSeconds(10));
        when(loginThrottle.status("member", "127.0.0.1"))
                .thenAnswer(invocation -> new LoginThrottleStatus(
                        6, Instant.now().plusSeconds(30)));

        Document page = renderWithoutStatusStub(state, true).document();

        assertThat(page.selectFirst("#loginFailureFeedback").text())
                .contains("로그인이 잠시 제한되었습니다.")
                .contains("보안을 위해")
                .contains("후 다시 시도할 수 있습니다.")
                .doesNotContain("현재 6회 실패했습니다.");
        assertThat(page.selectFirst("#loginFailureFeedback")
                .hasClass("login-feedback--locked")).isTrue();
        assertThat(page.selectFirst("#loginFailureFeedback")
                .attr("data-login-remaining-seconds"))
                .isEqualTo("30");
        assertThat(page.selectFirst("#loginThrottleCountdown")).isNotNull();
        assertThat(page.selectFirst("#loginFailureInline")).isNull();
        assertThat(page.selectFirst(".login-submit").hasAttr("disabled")).isTrue();
    }

    @Test
    void activeLimitRemainsVisibleWhenTheLoginPageIsReopenedWithoutTheErrorQuery() throws Exception {
        LoginFormState state = new LoginFormState(
                "member", 5, Instant.now().plusSeconds(10));

        Document page = render(state, false).document();

        assertThat(page.selectFirst("#loginThrottleCountdown")).isNotNull();
        assertThat(page.selectFirst(".login-submit").hasAttr("disabled")).isTrue();
    }

    @Test
    void loginPageUsesTheCurrentServerLimitInsteadOfTheStoredSnapshot() throws Exception {
        LoginFormState staleState = new LoginFormState(
                "member", 5, Instant.now().plusSeconds(10));
        when(loginThrottle.status("member", "127.0.0.1"))
                .thenAnswer(invocation -> new LoginThrottleStatus(
                        8, Instant.now().plusSeconds(300)));

        RenderedPage rendered = renderWithoutStatusStub(staleState, false);
        Document page = rendered.document();

        assertThat(page.selectFirst("#loginFailureFeedback").text())
                .doesNotContain("현재 8회 실패했습니다.");
        assertThat(page.selectFirst("#loginFailureFeedback")
                .attr("data-login-remaining-seconds"))
                .isEqualTo("300");
        assertThat(rendered.session().getAttribute(LoginFormState.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(LoginFormState.class,
                        state -> assertThat(state.failureCount()).isEqualTo(8));
    }

    @Test
    void currentIpLimitIsShownWithoutAStoredAccountFailure() throws Exception {
        when(loginThrottle.ipStatus("127.0.0.1"))
                .thenAnswer(invocation -> new LoginThrottleStatus(
                        0, Instant.now().plusSeconds(300)));

        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Document page = Jsoup.parse(html);

        assertThat(page.selectFirst("#loginThrottleCountdown")).isNotNull();
        assertThat(page.selectFirst(".login-submit").hasAttr("disabled")).isTrue();
    }

    @Test
    void refreshingAnIpOnlyLimitNeverMixesInTheEmptyAccountBucket() throws Exception {
        when(loginThrottle.ipStatus("127.0.0.1"))
                .thenAnswer(invocation -> new LoginThrottleStatus(
                        0, Instant.now().plusSeconds(60)));
        when(loginThrottle.status("", "127.0.0.1"))
                .thenAnswer(invocation -> new LoginThrottleStatus(
                        5, Instant.now().plusSeconds(300)));
        MockHttpSession session = new MockHttpSession();

        Document firstPage = renderEmptySession(session);
        Document refreshedPage = renderEmptySession(session);

        assertThat(firstPage.selectFirst("#loginFailureFeedback")
                .attr("data-login-remaining-seconds")).isEqualTo("60");
        assertThat(refreshedPage.selectFirst("#loginFailureFeedback")
                .attr("data-login-remaining-seconds")).isEqualTo("60");
        assertThat(session.getAttribute(LoginFormState.SESSION_ATTRIBUTE)).isNull();
        verify(loginThrottle, times(2)).ipStatus("127.0.0.1");
        verify(loginThrottle, never()).status("", "127.0.0.1");
    }

    private RenderedPage render(LoginFormState state) throws Exception {
        return render(state, true);
    }

    private RenderedPage render(LoginFormState state, boolean errorQuery) throws Exception {
        when(loginThrottle.status(state.username(), "127.0.0.1"))
                .thenReturn(new LoginThrottleStatus(
                        state.failureCount(), state.blockedUntil()));
        return renderWithoutStatusStub(state, errorQuery);
    }

    private RenderedPage renderWithoutStatusStub(LoginFormState state,
                                                  boolean errorQuery) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginFormState.SESSION_ATTRIBUTE, state);
        var request = get("/login").session(session);
        if (errorQuery) {
            request.param("error", "true");
        }
        String html = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new RenderedPage(Jsoup.parse(html), session);
    }

    private Document renderEmptySession(MockHttpSession session) throws Exception {
        String html = mockMvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }

    private record RenderedPage(Document document, MockHttpSession session) {
    }
}
