package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryPinService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIN 잠금 API.
 *
 * <p>어떤 응답에도 해시나 PIN 값이 실리지 않는다. 돌려주는 것은 성공 여부와 안내 문구뿐이고,
 * 소유자는 요청 값이 아니라 로그인 정보에서 온다.
 */
@WebMvcTest(DiaryPinController.class)
@Import(SecurityConfig.class)
class DiaryPinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryPinService diaryPinService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomUserDetails userDetails;

    @Test
    void guestIsSentToLogin() throws Exception {
        mockMvc.perform(post("/diaries/10/pin").param("newPin", "0427").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** PIN 설정. 소유자는 요청 값이 아니라 로그인 사용자다. */
    @Test
    void settingAPinUsesTheLoggedInUserAndNeverEchoesTheValue() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        String body = mockMvc.perform(post("/diaries/10/pin")
                        .param("newPin", "0427")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(diaryPinService).setPin(eq(10L), eq(7L), eq("0427"), any());
        // 되돌려 주는 것은 상태뿐이다
        assertThat(body).doesNotContain("0427").doesNotContain("pinHash").doesNotContain("$2");
    }

    /** 맞는 PIN 은 그 다이어리만 연다. */
    @Test
    void aCorrectPinUnlocksTheDiary() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPinService.verifyAndUnlock(eq(10L), eq(7L), eq("0427"), any())).thenReturn(true);

        mockMvc.perform(post("/diaries/10/pin/unlock")
                        .param("pin", "0427")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
    }

    /** 틀린 PIN 은 왜 틀렸는지 나누어 알려 주지 않는다. */
    @Test
    void aWrongPinIsRefusedWithOneMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPinService.verifyAndUnlock(eq(10L), eq(7L), any(), any())).thenReturn(false);

        String body = mockMvc.perform(post("/diaries/10/pin/unlock")
                        .param("pin", "9999")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("PIN 번호가 올바르지 않습니다.").doesNotContain("9999");
    }

    /** 잇달아 틀린 뒤에는 잠시 쉬어 간다. 그 이유가 그대로 화면에 전해진다. */
    @Test
    void tooManyTriesAreReportedToTheScreen() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPinService.verifyAndUnlock(eq(10L), eq(7L), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "PIN 입력 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."));

        mockMvc.perform(post("/diaries/10/pin/unlock")
                        .param("pin", "9999")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isTooManyRequests());
    }

    /** 변경과 영구 해제는 지금 PIN 을 함께 보낸다. */
    @Test
    void changingAndRemovingBothCarryTheCurrentPin() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pin/change")
                        .param("currentPin", "0427")
                        .param("newPin", "1234")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
        verify(diaryPinService).changePin(eq(10L), eq(7L), eq("0427"), eq("1234"), any());

        mockMvc.perform(post("/diaries/10/pin/remove")
                        .param("currentPin", "0427")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
        verify(diaryPinService).removePin(eq(10L), eq(7L), eq("0427"), any());
    }

    /** 토큰 없는 요청은 막힌다. (다른 다이어리 요청과 같은 정책) */
    @Test
    void aRequestWithoutATokenIsRefused() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pin/unlock")
                        .param("pin", "0427")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isForbidden());
    }
}
