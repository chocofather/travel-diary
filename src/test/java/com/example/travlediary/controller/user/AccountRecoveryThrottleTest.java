package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메일이 나갈 수 있는 계정 복구 요청의 한도.
 *
 * <p>여기서 보는 것은 두 가지다 — 주소를 바꿔 가며 같은 곳에서 퍼붓는 요청이 429 로 끝나는 것과,
 * 그 한도가 <b>회원을 찾아보기 전에</b> 걸려서 계정 존재 여부가 응답 차이로 드러나지 않는 것.
 * 같은 주소로의 60초 cooldown 은 {@code UserServiceAccountRecoveryTest} 가 맡는다.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InMemoryAccountAbuseGuard.class})
class AccountRecoveryThrottleTest {

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

    /** 평범한 요청은 예전 그대로 완료 화면으로 넘어간다. */
    @Test
    void anOrdinaryPasswordRecoveryStillRedirectsToTheRequestedScreen() throws Exception {
        mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "member@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.31")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-password"));

        verify(userService).processResetPasswordRequest("member@gmail.com");
    }

    /**
     * 있는 계정과 없는 계정의 응답이 같다.
     *
     * <p>서비스가 조용히 끝내든(없는 계정) 메일을 보내든(있는 계정) 밖에서 보이는 것은 같은
     * 302 와 같은 목적지뿐이다.
     */
    @Test
    void existingAndMissingAccountsAnswerIdentically() throws Exception {
        MvcResult existing = mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "member@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.32")))
                .andReturn();
        MvcResult missing = mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "nobody@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.32")))
                .andReturn();

        assertThat(existing.getResponse().getStatus())
                .isEqualTo(missing.getResponse().getStatus());
        assertThat(existing.getResponse().getRedirectedUrl())
                .isEqualTo(missing.getResponse().getRedirectedUrl());
        assertThat(existing.getResponse().getContentAsString())
                .isEqualTo(missing.getResponse().getContentAsString());
    }

    /** 복구 서비스가 실패해도 밖에서 보이는 응답은 달라지지 않는다. */
    @Test
    void aFailureInsideTheServiceDoesNotChangeWhatTheCallerSees() throws Exception {
        doThrow(new RuntimeException("mail down"))
                .when(userService).processResetPasswordRequest(anyString());

        mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "member@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.33")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/find-password"));
    }

    /**
     * 주소를 바꿔 가며 퍼부어도 같은 곳에서 온 요청이면 함께 걸린다.
     * 막힌 요청은 회원을 찾아보지도 않는다.
     */
    @Test
    void changingTheEmailDoesNotEscapeTheLimitForOneClient() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            mockMvc.perform(post("/users/find-password")
                            .param("userEmail", "victim" + attempt + "@gmail.com")
                            .with(csrf())
                            .with(from("203.0.113.34")))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "victim-last@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.34")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        verify(userService, never()).processResetPasswordRequest("victim-last@gmail.com");
    }

    /** 한 사람이 막혀도 다른 사람의 계정 복구는 그대로 된다. */
    @Test
    void anotherClientCanStillRecoverTheirAccount() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            mockMvc.perform(post("/users/find-password")
                            .param("userEmail", "victim" + attempt + "@gmail.com")
                            .with(csrf())
                            .with(from("203.0.113.36")))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "victim@gmail.com")
                        .with(csrf())
                        .with(from("203.0.113.36")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/users/find-password")
                        .param("userEmail", "member@gmail.com")
                        .with(csrf())
                        .with(from("198.51.100.40")))
                .andExpect(status().is3xxRedirection());
    }

    /* ===== 도우미 ===== */

    /*
      guard 는 Spring context 와 함께 테스트 사이에 재사용된다.
      한 테스트가 채운 창이 다음 테스트를 막지 않도록 테스트마다 다른 주소를 쓴다.
    */

    /** 제한은 IP 로 센다. 전달 헤더는 아직 믿지 않으므로 실제 접속 주소만 바꾼다. */
    private RequestPostProcessor from(String ipAddress) {
        return request -> {
            request.setRemoteAddr(ipAddress);
            return request;
        };
    }
}
