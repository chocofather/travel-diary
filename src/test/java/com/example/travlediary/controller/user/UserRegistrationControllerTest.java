package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.dto.RegistrationForm;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.RegistrationResult;
import com.example.travlediary.service.user.PasswordPolicy;
import com.example.travlediary.service.user.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserRegistrationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserController userController;
    @MockitoBean private UserService userService;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void successfulRegistrationStoresPendingSessionAndRedirectsToWaitingPage() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(validRegistrationRequest("member@gmail.com").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"))
                .andExpect(request().sessionAttribute(
                        EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                        "member@gmail.com"))
                .andExpect(flash().attribute("verificationMessageType", "success"));
    }

    @Test
    void mailFailureAfterSuccessfulInsertMovesToRecoverableWaitingPage() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", false));

        mockMvc.perform(validRegistrationRequest("member@gmail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"))
                .andExpect(request().sessionAttribute(
                        EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                        "member@gmail.com"))
                .andExpect(flash().attribute("verificationMessageType", "error"))
                .andExpect(flash().attribute("verificationMessage",
                        "회원가입은 완료되었지만 인증메일 발송 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @Test
    void invalidServerEmailReturnsFieldErrorWithoutCallingRegistration() throws Exception {
        mockMvc.perform(validRegistrationRequest("not-an-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "userEmail"));

        verify(userService, never()).registerUser(any());
    }

    @Test
    void failureBeforeRegistrationResultReturnsTheForm() throws Exception {
        when(userService.registerUser(any())).thenThrow(new IllegalStateException("save failed"));

        mockMvc.perform(validRegistrationRequest("member@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasErrors("registrationForm"));
    }

    @Test
    void postRegistrationSessionFailureNeverReturnsTheRegistrationForm() {
        RegistrationForm form = new RegistrationForm();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(form, "registrationForm");
        HttpSession session = mock(HttpSession.class);
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
        when(userService.registerUser(form))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        doThrow(new IllegalStateException("session unavailable"))
                .when(session).setAttribute(
                        EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE,
                        "member@gmail.com");

        String destination = userController.registerUser(
                form, bindingResult, null, session, redirectAttributes);

        assertThat(destination).isEqualTo("redirect:/users/verification/resend");
    }

    @Test
    void requiredTermsAreValidatedByTheServer() throws Exception {
        mockMvc.perform(multipart("/users/register")
                        .param("username", "member")
                        .param("userEmail", "member@gmail.com")
                        .param("userPassword", "Password!")
                        .param("passwordConfirm", "Password!")
                        .param("nickname", "여행자123")
                        .param("fullName", "여행자")
                        .param("userBirth", "1995-05-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors(
                        "registrationForm", "serviceTermsAccepted", "privacyTermsAccepted"));

        verify(userService, never()).registerUser(any());
    }

    @Test
    void invalidFullNameIsRejectedBeforeRegistration() throws Exception {
        mockMvc.perform(validRegistrationRequest("member@gmail.com")
                        .param("fullName", "김민준1"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "fullName"));

        verify(userService, never()).registerUser(any());
    }

    @Test
    void usernameRecoveryAcceptsEmailOnlyAndShowsTheGenericCompletionState() throws Exception {
        mockMvc.perform(post("/users/find-username")
                        .param("userEmail", "member@gmail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-username"))
                .andExpect(flash().attribute("recoveryRequested", true));

        verify(userService).processFindUsername("member@gmail.com");
    }

    @Test
    void passwordRecoveryShowsTheGenericCompletionState() throws Exception {
        mockMvc.perform(post("/users/find-password")
                        .param("username", "member")
                        .param("userEmail", "member@gmail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-password"))
                .andExpect(flash().attribute("recoveryRequested", true));

        verify(userService).processResetPasswordRequest("member", "member@gmail.com");
    }

    @Test
    void usernameRecoveryFailureUsesTheSameGenericCompletionState() throws Exception {
        doThrow(new IllegalStateException("recovery unavailable"))
                .when(userService).processFindUsername("member@gmail.com");

        mockMvc.perform(post("/users/find-username")
                        .param("userEmail", "member@gmail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-username"))
                .andExpect(flash().attribute("recoveryRequested", true));
    }

    @Test
    void passwordRecoveryFailureUsesTheSameGenericCompletionState() throws Exception {
        doThrow(new IllegalStateException("recovery unavailable"))
                .when(userService).processResetPasswordRequest("member", "member@gmail.com");

        mockMvc.perform(post("/users/find-password")
                        .param("username", "member")
                        .param("userEmail", "member@gmail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-password"))
                .andExpect(flash().attribute("recoveryRequested", true));
    }

    @Test
    void successfulPasswordResetRequiresConfirmationAndReturnsToLogin() throws Exception {
        mockMvc.perform(post("/users/reset-password")
                        .param("token", "safe-token")
                        .param("newPassword", "StrongPassword!")
                        .param("newPasswordConfirm", "StrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordChanged=true"));

        verify(userService).resetPassword(
                "safe-token", "StrongPassword!", "StrongPassword!");
    }

    @Test
    void resetPasswordPagePassesTheRawTokenThroughTheSharedValidationPath()
            throws Exception {
        when(userService.validateResetToken("safe-token")).thenReturn(new User());

        mockMvc.perform(get("/users/reset-password")
                        .param("token", "safe-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("token", "safe-token"))
                .andExpect(model().attribute(
                        "passwordPolicyMessage", PasswordPolicy.INVALID_MESSAGE));

        verify(userService).validateResetToken("safe-token");
    }

    @Test
    void mismatchedPasswordResetReturnsToTheFormWithAnError() throws Exception {
        doThrow(new IllegalArgumentException(PasswordPolicy.MISMATCH_MESSAGE))
                .when(userService).resetPassword(
                        "safe-token", "StrongPassword!", "DifferentPassword!");

        mockMvc.perform(post("/users/reset-password")
                        .param("token", "safe-token")
                        .param("newPassword", "StrongPassword!")
                        .param("newPasswordConfirm", "DifferentPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/reset-password?token=safe-token"))
                .andExpect(flash().attribute("error", PasswordPolicy.MISMATCH_MESSAGE));
    }

    @Test
    void invalidPasswordResetTokenKeepsTheExistingLoginErrorFlow() throws Exception {
        doThrow(new IllegalArgumentException(UserService.INVALID_RESET_TOKEN_MESSAGE))
                .when(userService).resetPassword(
                        "invalid-token", "StrongPassword!", "StrongPassword!");

        mockMvc.perform(post("/users/reset-password")
                        .param("token", "invalid-token")
                        .param("newPassword", "StrongPassword!")
                        .param("newPasswordConfirm", "StrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=invalid_token"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    validRegistrationRequest(String email) {
        return multipart("/users/register")
                .param("serviceTermsAccepted", "true")
                .param("privacyTermsAccepted", "true")
                .param("username", "member")
                .param("userEmail", email)
                .param("userPassword", "Password!")
                .param("passwordConfirm", "Password!")
                .param("nickname", "여행자123")
                .param("fullName", "여행자")
                .param("userPhone", "010-1234-5678")
                .param("userBirth", "1995-05-10");
    }
}
