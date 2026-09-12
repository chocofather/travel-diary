package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.email.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EmailVerificationController.class)
@Import(SecurityConfig.class)
class EmailVerificationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EmailVerificationService emailVerificationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void anonymousResendPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/users/verification/resend"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/users/verification/resend")
                        .param("email", "member@gmail.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCanOpenStandaloneResendPage() throws Exception {
        mockMvc.perform(get("/users/verification/resend"))
                .andExpect(status().isOk())
                .andExpect(view().name("verification-resend"));
    }

    @Test
    void anonymousUserWithPendingSessionCanOpenVerificationWaitingPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                "member@gmail.com");
        when(emailVerificationService.getWaitingState("member@gmail.com"))
                .thenReturn(new EmailVerificationService.WaitingState(
                        true, "mem***@gmail.com", 45));

        mockMvc.perform(get("/users/register/verify-waiting").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-waiting"));

        verify(emailVerificationService).getWaitingState("member@gmail.com");
    }

    @Test
    void resendUsesOnlyThePendingEmailStoredInTheServerSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                "member@gmail.com");
        when(emailVerificationService.resend("member@gmail.com"))
                .thenReturn(new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.SENT, 60));

        mockMvc.perform(post("/users/verification/resend")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"))
                .andExpect(flash().attribute("verificationMessageType", "success"));

        verify(emailVerificationService).resend("member@gmail.com");
    }

    @Test
    void deliveryFailureReturnsToWaitingPageWithSafeMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                "member@gmail.com");
        when(emailVerificationService.resend("member@gmail.com"))
                .thenReturn(new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.DELIVERY_FAILED, 60));

        mockMvc.perform(post("/users/verification/resend")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"))
                .andExpect(flash().attribute("verificationMessageType", "error"))
                .andExpect(flash().attribute("verificationMessage",
                        "인증메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @Test
    void standaloneResendWorksWithoutPendingSessionAndNormalizesEmail() throws Exception {
        when(emailVerificationService.resend("member@gmail.com"))
                .thenReturn(new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.SENT, 60));

        mockMvc.perform(post("/users/verification/resend")
                        .param("email", " MEMBER@GMAIL.COM ")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/verification/resend"))
                .andExpect(flash().attribute("verificationMessage",
                        EmailVerificationController.PUBLIC_RESEND_MESSAGE));

        verify(emailVerificationService).resend("member@gmail.com");
    }

    @Test
    void standaloneResendDoesNotRevealAccountOrDeliveryState() throws Exception {
        when(emailVerificationService.resend(anyString())).thenReturn(
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.SENT, 60),
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.NOT_ELIGIBLE, 0),
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.NOT_ELIGIBLE, 0),
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.NOT_ELIGIBLE, 0),
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.COOLDOWN, 30),
                new EmailVerificationService.ResendOutcome(
                        EmailVerificationService.ResendStatus.DELIVERY_FAILED, 60));

        for (String email : new String[]{
                "pending@gmail.com", "unknown@gmail.com", "active@gmail.com",
                "closed@gmail.com", "cooldown@gmail.com", "failure@gmail.com"}) {
            mockMvc.perform(post("/users/verification/resend")
                            .param("email", email)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/users/verification/resend"))
                    .andExpect(flash().attribute("verificationMessageType", "success"))
                    .andExpect(flash().attribute("verificationMessage",
                            EmailVerificationController.PUBLIC_RESEND_MESSAGE));
        }
    }

    /** 세션에 기다리는 이메일이 없으면 아무것도 알려주지 않는다. */
    @Test
    void verificationStatusWithoutAPendingSessionIsUnknown() throws Exception {
        mockMvc.perform(get("/users/verification/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UNKNOWN\"}"));

        verify(emailVerificationService, never()).checkProgress(anyString());
    }

    /** 조회 대상은 오직 세션이 기다리는 이메일이다. 요청 파라미터는 무시한다. */
    @Test
    void verificationStatusOnlyEverChecksTheEmailHeldInTheSession() throws Exception {
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenReturn(EmailVerificationService.VerificationProgress.PENDING);

        mockMvc.perform(get("/users/verification/status")
                        .param("email", "victim@gmail.com")
                        .param("userId", "17")
                        .session(pendingSession()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"PENDING\"}"));

        verify(emailVerificationService).checkProgress("member@gmail.com");
        verify(emailVerificationService, never()).checkProgress("victim@gmail.com");
    }

    @Test
    void verificationStatusReportsVerifiedAndReleasesTheWaitingSession() throws Exception {
        MockHttpSession session = pendingSession();
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenReturn(EmailVerificationService.VerificationProgress.VERIFIED);

        mockMvc.perform(get("/users/verification/status").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"VERIFIED\"}"));

        assertThat(session.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
    }

    /** 응답 본문은 status 한 줄뿐이다. 회원 존재 여부나 내부 상태는 새어 나가지 않는다. */
    @Test
    void verificationStatusBodyCarriesNothingBesidesTheThreeAllowedValues() throws Exception {
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenReturn(EmailVerificationService.VerificationProgress.UNKNOWN);

        String body = mockMvc.perform(get("/users/verification/status").session(pendingSession()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("{\"status\":\"UNKNOWN\"}")
                .doesNotContain("member@gmail.com", "SUSPENDED", "RESTRICTED",
                        "WITHDRAWAL_PENDING", "DEACTIVATED", "INACTIVE", "userId");
    }

    /** 조회가 실패해도 상태를 흘리지 않고 UNKNOWN 으로 답한다. */
    @Test
    void verificationStatusFailureStaysUnknownInsteadOfErroring() throws Exception {
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenThrow(new IllegalStateException("db down"));

        mockMvc.perform(get("/users/verification/status").session(pendingSession()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UNKNOWN\"}"));
    }


    /**
     * 같은 브라우저의 새 탭이 인증을 끝내도 대기 탭의 polling 문맥은 남아 있어야 한다.
     * 세션은 탭끼리 공유되므로 여기서 지우면 대기 탭이 영영 완료를 감지하지 못한다.
     */
    @Test
    void verifyingInAnotherTabKeepsThePollingContextForTheWaitingTab() throws Exception {
        MockHttpSession session = pendingSession();
        when(emailVerificationService.verify("valid-token")).thenReturn(
                new EmailVerificationService.VerificationOutcome(
                        EmailVerificationService.VerificationStatus.SUCCESS, "member@gmail.com"));

        mockMvc.perform(get("/users/verify").param("token", "valid-token").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("verification-result"));

        assertThat(session.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE))
                .isEqualTo("member@gmail.com");
    }

    /** 대기 탭의 다음 polling 이 ACTIVE 를 확인하고, 그때 비로소 문맥을 닫는다. */
    @Test
    void theWaitingTabThenSeesVerifiedAndTheContextIsClosedInThatOrder() throws Exception {
        MockHttpSession session = pendingSession();
        when(emailVerificationService.verify("valid-token")).thenReturn(
                new EmailVerificationService.VerificationOutcome(
                        EmailVerificationService.VerificationStatus.SUCCESS, "member@gmail.com"));
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenReturn(EmailVerificationService.VerificationProgress.VERIFIED);

        mockMvc.perform(get("/users/verify").param("token", "valid-token").session(session));
        mockMvc.perform(get("/users/verification/status").session(session))
                .andExpect(content().json("{\"status\":\"VERIFIED\"}"));

        assertThat(session.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
    }

    /**
     * 다른 브라우저나 기기에서 인증한 경우. 대기 브라우저의 세션은 그대로이므로
     * 자기 세션의 이메일로 DB 상태를 조회해 ACTIVE 를 알아챈다.
     */
    @Test
    void verifyingOnAnotherDeviceIsStillDetectedFromTheWaitingSession() throws Exception {
        MockHttpSession waiting = pendingSession();
        when(emailVerificationService.checkProgress("member@gmail.com"))
                .thenReturn(EmailVerificationService.VerificationProgress.PENDING)
                .thenReturn(EmailVerificationService.VerificationProgress.VERIFIED);

        mockMvc.perform(get("/users/verification/status").session(waiting))
                .andExpect(content().json("{\"status\":\"PENDING\"}"));
        assertThat(waiting.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE))
                .isEqualTo("member@gmail.com");

        mockMvc.perform(get("/users/verification/status").session(waiting))
                .andExpect(content().json("{\"status\":\"VERIFIED\"}"));
        assertThat(waiting.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
    }

    /** 인증이 끝난 뒤 대기 화면을 새로고침해도 polling 은 계속 붙는다. */
    @Test
    void theWaitingPageKeepsPollingEvenWhenTheAccountIsNoLongerPending() throws Exception {
        MockHttpSession session = pendingSession();
        when(emailVerificationService.getWaitingState(null))
                .thenReturn(new EmailVerificationService.WaitingState(false, "", 0));
        when(emailVerificationService.getWaitingState("member@gmail.com"))
                .thenReturn(new EmailVerificationService.WaitingState(false, "", 0));

        mockMvc.perform(get("/users/register/verify-waiting").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("verificationAvailable", false))
                .andExpect(model().attribute("verificationPollingAvailable", true));

        // 기다릴 이메일이 아예 없으면 폴링도 걸지 않는다.
        mockMvc.perform(get("/users/register/verify-waiting"))
                .andExpect(model().attribute("verificationPollingAvailable", false));
    }

    private MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                "member@gmail.com");
        return session;
    }

    @Test
    void malformedStandaloneEmailShowsOnlyFormatValidation() throws Exception {
        mockMvc.perform(post("/users/verification/resend")
                        .param("email", "not-an-email")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/verification/resend"))
                .andExpect(flash().attribute("emailError", "올바른 이메일 주소를 입력해주세요."));

        verify(emailVerificationService, never()).resend(anyString());
    }
}
