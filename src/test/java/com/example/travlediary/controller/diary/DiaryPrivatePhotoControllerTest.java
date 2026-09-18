package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.DiaryPinLockedAdvice;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.WebConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryPinLockedException;
import com.example.travlediary.service.diary.DiaryPrivatePhotoService;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개인 다이어리 사진을 내보내는 통제된 문의 계약.
 *
 * <p>여기서 확인하는 것은 세 가지다 — 로그인하지 않으면 닿지 못하고, 서비스가 거절한 요청은
 * 이유를 구분할 수 없는 404 이며, 실제로 나가는 응답에는 개인 사진에 맞는 머리말이 붙는다.
 * 소유권과 PIN 판단 자체는 {@code DiaryPrivatePhotoServiceImplTest} 가 맡는다.
 */
@WebMvcTest(controllers = DiaryPrivatePhotoController.class)
@Import({SecurityConfig.class, WebConfig.class, DiaryPinLockedAdvice.class})
class DiaryPrivatePhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryPrivatePhotoService privatePhotoService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private CustomUserDetails userDetails;

    @TempDir
    Path storageRoot;

    private Path photo;

    @BeforeEach
    void setUp() throws Exception {
        photo = storageRoot.resolve("photo.jpg");
        Files.write(photo, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16});
    }

    /** 네 경로 모두 로그인 없이는 닿지 못한다. */
    @Test
    void everyPhotoEndpointRequiresLogin() throws Exception {
        for (String url : endpoints()) {
            mockMvc.perform(get(url))
                    .andExpect(status().is3xxRedirection());
        }
    }

    /** 본인 사진은 개인 사진에 맞는 머리말과 함께 나간다. */
    @Test
    void anOwnedPhotoIsServedWithPrivateHeaders() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(privatePhotoService.getPageElementPhoto(10L, 1L, 101L, 7L)).thenReturn(storedFile());

        mockMvc.perform(get("/diaries/10/pages/1/elements/101/photo").with(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "6"))
                // 개인 사진이라 공유 캐시에 남기지 않는다
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "max-age=60, must-revalidate, private"))
                // 브라우저가 다른 형식으로 해석하지 못하게 한다
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
    }

    /** 네 경로 모두 같은 머리말 규칙을 쓴다. */
    @Test
    void allFourEndpointsUseTheSameResponseRules() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(privatePhotoService.getCoverImage(anyLong(), anyLong())).thenReturn(storedFile());
        when(privatePhotoService.getPageElementPhoto(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(storedFile());
        when(privatePhotoService.getCoverElementPhoto(anyLong(), anyLong(), anyLong()))
                .thenReturn(storedFile());
        when(privatePhotoService.getCoverDesignElementPhoto(anyLong(), anyLong(), anyLong()))
                .thenReturn(storedFile());

        for (String url : endpoints()) {
            mockMvc.perform(get(url).with(loggedIn()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
        }
    }

    /** 서비스가 거절한 요청은 이유를 구분할 수 없는 404 다. */
    @Test
    void aRefusedLookupIsAPlain404() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(privatePhotoService.getPageElementPhoto(10L, 1L, 999L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "사진을 찾을 수 없습니다."));

        mockMvc.perform(get("/diaries/10/pages/1/elements/999/photo").with(loggedIn()))
                .andExpect(status().isNotFound());
    }

    /** 본인의 잠긴 다이어리만 403 이다. (화면의 PIN 정책과 같은 값) */
    @Test
    void aLockedDiaryPhotoIsForbiddenNotHidden() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(privatePhotoService.getPageElementPhoto(10L, 1L, 101L, 7L))
                .thenThrow(new DiaryPinLockedException(10L));

        mockMvc.perform(get("/diaries/10/pages/1/elements/101/photo").with(loggedIn()))
                .andExpect(status().isForbidden());
    }

    /** 파일 이름이나 저장 키를 주소로 받지 않는다. 숫자 번호가 아닌 경로는 아예 붙지 않는다. */
    @Test
    void onlyNumericIdentifiersReachTheseEndpoints() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        for (String url : List.of(
                "/diaries/abc/cover-image",
                "/diaries/10/pages/1/elements/a.jpg/photo",
                "/diaries/10/cover/elements/..%2Fsecret/photo",
                "/diaries/cover-designs/x/elements/1/photo")) {
            // 경로 자체가 붙지 않아 404 이거나, 요청 방화벽이 먼저 400 으로 끊는다.
            // 어느 쪽이든 서비스와 저장소에는 닿지 않는다.
            mockMvc.perform(get(url).with(loggedIn()))
                    .andExpect(status().is4xxClientError());
        }
        verifyNoInteractions(privatePhotoService);
    }

    /* ===== 도우미 ===== */

    private List<String> endpoints() {
        return List.of(
                "/diaries/10/cover-image",
                "/diaries/10/pages/1/elements/101/photo",
                "/diaries/10/cover/elements/55/photo",
                "/diaries/cover-designs/5/elements/101/photo");
    }

    private DiaryPrivatePhotoStorage.StoredPhotoFile storedFile() {
        return new DiaryPrivatePhotoStorage.StoredPhotoFile(photo, "image/jpeg", 6L);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of()));
    }
}
