package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.GuestDiaryImportManifest;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.GuestDiaryImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가져오기 입구의 규칙.
 *
 * <p>지키는 것은 네 가지다. 로그인한 사람만 들어온다는 것, 소유자는 인증 정보로만 정해진다는 것,
 * 저장 요청에도 CSRF 가 걸린다는 것, 그리고 같은 표로 두 번 저장되지 않는다는 것.
 */
@WebMvcTest(GuestDiaryImportController.class)
@Import(SecurityConfig.class)
class GuestDiaryImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GuestDiaryImportService guestDiaryImportService;
    /** 확인 화면의 미리보기가 쓰는 마스킹테이프 조각 표. (manifest 를 읽는 카탈로그다) */
    @MockitoBean
    private com.example.travlediary.service.diary.DiaryStickerCatalog diaryStickerCatalog;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomUserDetails userDetails;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        when(userDetails.getId()).thenReturn(7L);
    }

    /** 8) 확인 화면은 로그인해야 열린다. */
    @Test
    void theConfirmScreenIsForSignedInMembersOnly() throws Exception {
        mockMvc.perform(get("/diaries/import"))
                .andExpect(status().is3xxRedirection());
    }

    /** 화면을 열면 가져오기 표를 한 장 끊어 준다. */
    @Test
    void openingTheScreenIssuesAnImportToken() throws Exception {
        mockMvc.perform(get("/diaries/import").session(session).with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("importToken"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "내 여행일기로 저장하기")));
    }

    /** 1) 로그인하지 않은 저장 요청은 서비스까지 오지 않는다. */
    @Test
    void anAnonymousImportIsBlockedBeforeTheServiceIsCalled() throws Exception {
        mockMvc.perform(importRequest("any-token", "{}").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(guestDiaryImportService);
    }

    /** 4) CSRF 토큰이 없는 저장 요청도 받지 않는다. */
    @Test
    void anImportWithoutACsrfTokenIsRejected() throws Exception {
        mockMvc.perform(importRequest("any-token", "{}").with(signedIn()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(guestDiaryImportService);
    }

    /**
     * 2) 3) 저장되는 여행일기의 주인은 로그인 정보로만 정해진다.
     *
     * <p>보내온 값에 소유자를 적어 넣어도 읽지 않는다. 모르는 칸은 무시하기 때문이다.
     */
    @Test
    void theOwnerComesFromTheSessionNotFromThePayload() throws Exception {
        when(guestDiaryImportService.importDraft(anyLong(), any(), any())).thenReturn(42L);
        String token = issuedToken();

        mockMvc.perform(importRequest(token, manifestJson("\"userId\": 999, \"ownerId\": 999,"))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.diaryId").value(42));

        verify(guestDiaryImportService).importDraft(eq(7L), any(), any());
    }

    /** 30) 이 세션에서 끊어 주지 않은 표로는 저장되지 않는다. */
    @Test
    void anImportTokenThatWasNeverIssuedIsRejected() throws Exception {
        mockMvc.perform(importRequest("made-up-token", manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(guestDiaryImportService);
    }

    /** 30) 다른 세션에서 끊은 표도 남의 세션에서는 쓸 수 없다. */
    @Test
    void anImportTokenFromAnotherSessionDoesNotWorkHere() throws Exception {
        String token = issuedToken();

        mockMvc.perform(importRequest(token, manifestJson(""))
                        .session(new MockHttpSession()).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(guestDiaryImportService);
    }

    /** 29) 같은 표가 두 번 오면 한 권을 더 만들지 않고 그때의 결과를 돌려준다. */
    @Test
    void theSameTokenNeverSavesTwice() throws Exception {
        when(guestDiaryImportService.importDraft(anyLong(), any(), any())).thenReturn(42L);
        String token = issuedToken();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(importRequest(token, manifestJson(""))
                            .session(session).with(signedIn()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.diaryId").value(42));
        }

        verify(guestDiaryImportService, times(1)).importDraft(anyLong(), any(), any());
    }

    /** 31) 실패했으면 표는 아직 남아 있다. 같은 표로 다시 시도할 수 있다. */
    @Test
    void aFailedImportLeavesTheTokenUsable() throws Exception {
        when(guestDiaryImportService.importDraft(anyLong(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 정보가 맞지 않습니다."))
                .thenReturn(42L);
        String token = issuedToken();

        mockMvc.perform(importRequest(token, manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("사진 정보가 맞지 않습니다."));

        mockMvc.perform(importRequest(token, manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaryId").value(42));

        verify(guestDiaryImportService, times(2)).importDraft(anyLong(), any(), any());
    }

    /** 읽을 수 없는 내용은 서비스까지 보내지 않는다. */
    @Test
    void anUnreadableManifestIsRejectedAtTheDoor() throws Exception {
        String token = issuedToken();

        mockMvc.perform(importRequest(token, "not-json-at-all")
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("가져올 여행일기 정보를 읽지 못했습니다."));

        verifyNoInteractions(guestDiaryImportService);
    }

    /**
     * 사진은 정해진 이름표의 칸으로만 올라온다.
     *
     * <p>파일 이름이나 칸 이름을 저장 경로로 쓰지 않지만, 모르는 칸을 그냥 지나치면
     * 무엇이 올라왔는지 서버가 세지 못하게 된다. 그래서 여기에서 끊는다.
     */
    @Test
    void fileParcelsOutsideTheAgreedNamesAreRejected() throws Exception {
        String token = issuedToken();

        mockMvc.perform(multipart("/diaries/import")
                        .file(new MockMultipartFile("attachment", "a.jpg", "image/jpeg",
                                new byte[]{1}))
                        .param("importToken", token)
                        .param("manifest", manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사진 정보가 맞지 않습니다."));

        verifyNoInteractions(guestDiaryImportService);
    }

    /** 한 칸에 파일이 여럿이면 어느 쪽이 무엇인지 알 수 없다. */
    @Test
    void aSinglePartCarryingTwoFilesIsRejected() throws Exception {
        String token = issuedToken();

        mockMvc.perform(multipart("/diaries/import")
                        .file(new MockMultipartFile("photo0", "a.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("photo0", "b.jpg", "image/jpeg", new byte[]{2}))
                        .param("importToken", token)
                        .param("manifest", manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(guestDiaryImportService);
    }

    /** 예기치 못한 실패는 안쪽 사정을 그대로 내보내지 않는다. */
    @Test
    void anUnexpectedFailureDoesNotLeakItsDetails() throws Exception {
        when(guestDiaryImportService.importDraft(anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("jdbc://secret-host/mydb 연결 실패"));
        String token = issuedToken();

        mockMvc.perform(importRequest(token, manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "여행일기를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
    }

    /** 보내온 내용은 DTO 로만 받는다. 소유자 칸이 없으므로 채워 넣을 자리도 없다. */
    @Test
    void theManifestReachesTheServiceAsItWasSent() throws Exception {
        when(guestDiaryImportService.importDraft(anyLong(), any(), any())).thenReturn(42L);
        String token = issuedToken();

        mockMvc.perform(importRequest(token, manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<GuestDiaryImportManifest> captor =
                org.mockito.ArgumentCaptor.forClass(GuestDiaryImportManifest.class);
        verify(guestDiaryImportService).importDraft(eq(7L), captor.capture(), any());
        assertThat(captor.getValue().title()).isEqualTo("체험 여행일기");
        assertThat(captor.getValue().pages()).hasSize(1);
    }

    /** 표를 들고 오지 않은 요청도 받지 않는다. */
    @Test
    void animportWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(multipart("/diaries/import")
                        .param("manifest", manifestJson(""))
                        .session(session).with(signedIn()).with(csrf()))
                .andExpect(status().isBadRequest());

        verify(guestDiaryImportService, never()).importDraft(anyLong(), any(), any());
    }

    /* ===== 도우미 ===== */

    private String issuedToken() throws Exception {
        return (String) mockMvc.perform(get("/diaries/import").session(session).with(signedIn()))
                .andReturn().getModelAndView().getModel().get("importToken");
    }

    private MockHttpServletRequestBuilder importRequest(String token, String manifest) {
        return multipart("/diaries/import")
                .param("importToken", token)
                .param("manifest", manifest);
    }

    private String manifestJson(String extraFields) {
        return "{" + extraFields + "\"title\":\"체험 여행일기\","
                + "\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-03\","
                + "\"coverType\":\"PRESET\",\"coverStyle\":\"DEFAULT\","
                + "\"pages\":[{\"pageDate\":\"2026-03-01\",\"elements\":[]}],"
                + "\"photoParts\":{}}";
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of()));
    }
}
