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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
