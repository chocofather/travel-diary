package com.example.travlediary.config;

import com.example.travlediary.security.AccountAbuseGuard;
import com.example.travlediary.controller.user.UserController;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.policy.SignupPolicyFixtures;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.user.RegistrationResult;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 는 목록이 아니라 기본 정책으로 걸린다.
 *
 * <p>GET 은 그대로 열려 있고, 토큰 없는 POST 는 로그인 전이든 후든 403 으로 끝난다.
 * 비로그인 사용자가 보내는 로그인·회원가입 폼도 예외가 아니다.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class CsrfPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    // 요청 남용 제한은 이 화면 계약의 관심사가 아니라 통과시키는 가짜를 쓴다.
    @MockitoBean
    private AccountAbuseGuard accountAbuseGuard;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private SignupPolicyService signupPolicyService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    /** 공개 GET 화면은 토큰 없이도 그대로 열린다. */
    @Test
    void publicGetsAreNotAffected() throws Exception {
        when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicyFixtures.activeSignupPolicies());

        mockMvc.perform(get("/users/register"))
                .andExpect(status().isOk());
    }

    /** 로그인 폼도 토큰을 요구한다. 남의 사이트가 대신 로그인시킬 수 없다. */
    @Test
    void theLoginPostNeedsAToken() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "member")
                        .param("password", "Password!"))
                .andExpect(status().isForbidden());

        // 토큰이 있으면 CSRF 를 지나 인증 단계로 간다 (성공/실패는 자격 증명이 정한다)
        mockMvc.perform(post("/login")
                        .param("username", "member")
                        .param("password", "Password!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** 비로그인 회원가입 POST 도 토큰 없이는 서비스까지 가지 않는다. */
    @Test
    void theAnonymousSignupPostNeedsAToken() throws Exception {
        mockMvc.perform(post("/users/register")
                        .param("username", "member")
                        .param("userEmail", "member@gmail.com")
                        .param("userPassword", "Password!")
                        .param("passwordConfirm", "Password!")
                        .param("nickname", "여행자123")
                        .param("birthDate", "2000-01-01"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);

        // 토큰이 있으면 평소처럼 컨트롤러와 서비스까지 간다
        when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicyFixtures.activeSignupPolicies());
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        mockMvc.perform(post("/users/register")
                        .param("username", "member")
                        .param("userEmail", "member@gmail.com")
                        .param("userPassword", "Password!")
                        .param("passwordConfirm", "Password!")
                        .param("nickname", "여행자123")
                        .param("birthDate", "2000-01-01")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(userService).registerUser(any());
    }

    /** 로그아웃도 POST 라 토큰을 요구한다. (정상 로그아웃은 LogoutSecurityTest 가 본다) */
    @Test
    void theLogoutPostNeedsAToken() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}
