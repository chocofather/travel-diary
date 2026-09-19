package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.policy.SignupPolicyFixtures;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.user.RegistrationResult;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 최초 가입 인증메일의 요청 한도.
 *
 * <p>가입이 끝나면 인증메일이 한 통 나간다. 주소를 바꿔 가며 가입을 되풀이하면 SMTP 한도가
 * 바닥나고, 그 순간 정상 회원의 비밀번호 재설정 메일까지 함께 막힌다. 그래서 계정 복구 메일과
 * <b>같은 IP 통</b>에서 센다 — 쓰는 SMTP 자원이 하나이기 때문이다.
 *
 * <p>소셜 가입이 같은 통을 쓰는지는 {@code SocialSignupMailThrottleTest} 가 이어서 본다.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InMemoryAccountAbuseGuard.class})
class SignupMailThrottleTest {

    private static final int LIMIT = InMemoryAccountAbuseGuard.RECOVERY_REQUEST_LIMIT;

    @Autowired
    private MockMvc mockMvc;

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

    @BeforeEach
    void setUp() {
        when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicyFixtures.activeSignupPolicies());
    }

    /** 평범한 가입은 예전 그대로 인증 대기 화면으로 넘어간다. */
    @Test
    void anOrdinarySignupStillCompletes() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));

        mockMvc.perform(signup("member@gmail.com").with(from("203.0.113.61")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"));

        verify(userService).registerUser(any());
    }

    /**
     * 입력 형식 오류는 메일과 무관하므로 통을 소모하지 않는다.
     *
     * <p>한도만큼 잘못된 가입을 보낸 뒤에도 정상 가입 한 건은 그대로 지나가야 한다.
     */
    @Test
    void validationErrorsNeverConsumeTheMailBudget() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));

        for (int attempt = 0; attempt <= LIMIT; attempt++) {
            mockMvc.perform(signup("not-an-email").with(from("203.0.113.62")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("register"));
        }

        mockMvc.perform(signup("member@gmail.com").with(from("203.0.113.62")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/register/verify-waiting"));
    }

    /** 주소를 바꿔 가며 가입을 되풀이하면 같은 곳에서 온 요청은 함께 걸린다. */
    @Test
    void repeatingSignupsWithFreshEmailsIsCappedPerClient() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        fillLimit("203.0.113.63");

        mockMvc.perform(signup("victim-last@gmail.com").with(from("203.0.113.63")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(view().name("register"));

        // 막힌 요청은 계정을 만들지도, 메일을 보내지도 않는다
        verify(userService, never()).registerUser(
                org.mockito.ArgumentMatchers.argThat(
                        form -> "victim-last@gmail.com".equals(form.getUserEmail())));
    }

    /** 가입과 계정 복구는 같은 통을 쓴다. 가입으로 채우면 복구 메일도 함께 막힌다. */
    @Test
    void signupAndAccountRecoveryShareOneLimit() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        fillLimit("203.0.113.64");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/users/find-password")
                        .param("userEmail", "member@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.64")))
                .andExpect(status().isTooManyRequests());

        verify(userService, never()).processResetPasswordRequest(
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 한 사람이 막혀도 다른 사람의 가입은 그대로 된다. */
    @Test
    void anotherClientCanStillSignUp() throws Exception {
        when(userService.registerUser(any()))
                .thenReturn(new RegistrationResult("member@gmail.com", true));
        fillLimit("203.0.113.65");

        mockMvc.perform(signup("blocked@gmail.com").with(from("203.0.113.65")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(signup("welcome@gmail.com").with(from("198.51.100.70")))
                .andExpect(status().is3xxRedirection());
    }

    /* ===== 도우미 ===== */

    /*
      guard 는 Spring context 와 함께 테스트 사이에 재사용된다.
      한 테스트가 채운 창이 다음 테스트를 막지 않도록 테스트마다 다른 주소를 쓴다.
    */

    private void fillLimit(String ipAddress) throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            mockMvc.perform(signup("victim" + attempt + "@gmail.com").with(from(ipAddress)))
                    .andExpect(status().is3xxRedirection());
        }
    }

    private MockHttpServletRequestBuilder signup(String email) {
        return multipart("/users/register")
                .param("agreedPolicyVersionIds", "101")
                .param("agreedPolicyVersionIds", "103")
                .param("birthDate", "2000-01-01")
                .param("userEmail", email)
                .param("userPassword", "Password!")
                .param("passwordConfirm", "Password!")
                .param("nickname", "여행자123")
                .with(csrf());
    }

    /** 제한은 클라이언트 주소로 센다. 전달 헤더는 믿지 않으므로 실제 접속 주소만 바꾼다. */
    private RequestPostProcessor from(String ipAddress) {
        return request -> {
            request.setRemoteAddr(ipAddress);
            return request;
        };
    }
}
