package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비로그인으로 열려 있는 회원 존재 확인 조회의 한도.
 *
 * <p>가입 폼은 300ms debounce 뒤에야 한 번 부르므로 사람이 한 화면을 채우는 동안에는 한도에
 * 닿지 않는다. 여기서는 그 정상 사용이 막히지 않는 것과, 훑어보는 자동화가 429 로 끝나는 것을
 * 함께 본다. 창이 지난 뒤 다시 열리는지는 {@code InMemoryAccountAbuseGuardTest} 가 맡는다.
 */
@WebMvcTest(UserApiController.class)
@Import({SecurityConfig.class, InMemoryAccountAbuseGuard.class})
class UserApiThrottleTest {

    private static final int LIMIT = InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    /** 평범한 조회는 그대로 답한다. */
    @Test
    void anOrdinaryLookupStillAnswers() throws Exception {
        when(userService.isUsernameExists("traveler")).thenReturn(false);

        mockMvc.perform(get("/api/users/check-username")
                        .param("username", "traveler")
                        .with(from("203.0.113.1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    /**
     * 한 화면을 채우는 만큼의 조회로는 막히지 않는다.
     * (아이디·이메일·닉네임을 고쳐 가며 스무 번씩 불러도 한도 안이다)
     */
    @Test
    void oneSignupFormSessionNeverReachesTheLimit() throws Exception {
        when(userService.isUsernameExists(anyString())).thenReturn(false);
        when(userService.isNicknameExists(anyString())).thenReturn(false);
        when(userService.isEmailExists(anyString())).thenReturn(false);

        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(get("/api/users/check-username")
                            .param("username", "traveler" + attempt)
                            .with(from("203.0.113.2")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/users/check-nickname")
                        .param("nickname", "여행왕123")
                        .with(from("203.0.113.2")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/check-email")
                        .param("email", "member@gmail.com")
                        .with(from("203.0.113.2")))
                .andExpect(status().isOk());
    }

    /** 한도를 넘긴 조회는 429 로 끝나고 다시 시도할 시각만 알려 준다. */
    @Test
    void lookupsBeyondTheLimitAnswerWith429AndRetryAfter() throws Exception {
        when(userService.isUsernameExists(anyString())).thenReturn(false);
        fillLimit("203.0.113.3");

        mockMvc.perform(get("/api/users/check-username")
                        .param("username", "traveler")
                        .with(from("203.0.113.3")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").exists())
                // 회원이 있는지, 어떤 한도에 걸렸는지는 담지 않는다
                .andExpect(jsonPath("$.exists").doesNotExist())
                .andExpect(jsonPath("$.limit").doesNotExist());
    }

    /** 세 조회는 같은 한도를 나눠 쓴다. 하나로 채우면 나머지도 함께 막힌다. */
    @Test
    void allThreeLookupsShareOneLimit() throws Exception {
        when(userService.isUsernameExists(anyString())).thenReturn(false);
        fillLimit("203.0.113.4");

        mockMvc.perform(get("/api/users/check-email")
                        .param("email", "member@gmail.com")
                        .with(from("203.0.113.4")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/users/check-nickname")
                        .param("nickname", "여행왕123")
                        .with(from("203.0.113.4")))
                .andExpect(status().isTooManyRequests());
    }

    /** 한 사람이 막혀도 다른 사람의 가입은 그대로 진행된다. */
    @Test
    void anotherClientIsNotBlockedAlong() throws Exception {
        when(userService.isUsernameExists(anyString())).thenReturn(false);
        fillLimit("203.0.113.5");

        mockMvc.perform(get("/api/users/check-username")
                        .param("username", "traveler")
                        .with(from("203.0.113.5")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/users/check-username")
                        .param("username", "traveler")
                        .with(from("198.51.100.20")))
                .andExpect(status().isOk());
    }

    /* ===== 도우미 ===== */

    /*
      guard 는 Spring context 와 함께 테스트 사이에 재사용된다.
      한 테스트가 채운 창이 다음 테스트를 막지 않도록 테스트마다 다른 주소를 쓴다.
    */

    private void fillLimit(String ipAddress) throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            mockMvc.perform(get("/api/users/check-username")
                            .param("username", "traveler" + attempt)
                            .with(from(ipAddress)))
                    .andExpect(status().isOk());
        }
    }

    /** 제한은 IP 로 센다. 전달 헤더는 아직 믿지 않으므로 실제 접속 주소만 바꾼다. */
    private RequestPostProcessor from(String ipAddress) {
        return request -> {
            request.setRemoteAddr(ipAddress);
            return request;
        };
    }
}
