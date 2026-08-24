package com.example.travlediary.controller.travelplan;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.TravelPlanInvitePreviewDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanInvitationService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 초대 링크 엔드포인트.
 * 권한 판단은 Service 가 하고 Controller 는 링크 조립과 화면 선택만 맡는다.
 */
@WebMvcTest(TravelPlanInvitationController.class)
@Import(SecurityConfig.class)
class TravelPlanInvitationControllerTest {

    private static final String RAW_TOKEN = "Zm9vYmFyLXRva2VuLXNhbXBsZS12YWx1ZS0xMjM0NTY3ODkw";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelPlanInvitationService travelPlanInvitationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void issuingALinkShowsTheRawUrlExactlyOnce() throws Exception {
        when(travelPlanInvitationService.createInvitation(7L, 42L)).thenReturn(RAW_TOKEN);

        mockMvc.perform(post("/travel-plans/42/invitations")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"))
                // 발급 응답에서만 볼 수 있다 (flash 라 새로고침하면 사라진다)
                .andExpect(flash().attribute("travelPlanInviteUrl",
                        "http://localhost/travel-plans/invitations/" + RAW_TOKEN))
                .andExpect(flash().attributeExists("travelPlanMessage"));

        verify(travelPlanInvitationService).createInvitation(7L, 42L);
    }

    @Test
    void aRefusedSecondLinkIsShownAsAMessageInsteadOfAnErrorPage() throws Exception {
        when(travelPlanInvitationService.createInvitation(anyLong(), anyLong()))
                .thenThrow(new TravelPlanValidationException("invitation",
                        "이미 활성화된 초대 링크가 있습니다. 새로 만들려면 재발급해 주세요."));

        mockMvc.perform(post("/travel-plans/42/invitations")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"))
                .andExpect(flash().attributeExists("travelPlanError"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void regeneratingHandsBackTheNewUrl() throws Exception {
        when(travelPlanInvitationService.regenerateInvitation(7L, 42L)).thenReturn(RAW_TOKEN);

        mockMvc.perform(post("/travel-plans/42/invitations/regenerate")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("travelPlanInviteUrl",
                        "http://localhost/travel-plans/invitations/" + RAW_TOKEN));

        verify(travelPlanInvitationService).regenerateInvitation(7L, 42L);
    }

    @Test
    void disablingReportsBackWithoutAnyUrl() throws Exception {
        mockMvc.perform(post("/travel-plans/42/invitations/disable")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"))
                .andExpect(flash().attributeExists("travelPlanMessage"))
                // 끌 때는 보여 줄 링크가 없다
                .andExpect(flash().attributeCount(1));

        verify(travelPlanInvitationService).disableInvitation(7L, 42L);
    }

    @Test
    void aNonOwnerIsRefusedByTheServiceAndNeverSeesALink() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."))
                .when(travelPlanInvitationService).disableInvitation(anyLong(), anyLong());
        when(travelPlanInvitationService.createInvitation(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));

        mockMvc.perform(post("/travel-plans/42/invitations")
                        .with(user(member())).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/travel-plans/42/invitations/disable")
                        .with(user(member())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyInvitationPostIsCsrfProtected() throws Exception {
        mockMvc.perform(post("/travel-plans/42/invitations").with(user(member())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/invitations/regenerate").with(user(member())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/invitations/disable").with(user(member())))
                .andExpect(status().isForbidden());

        verify(travelPlanInvitationService, never()).createInvitation(anyLong(), anyLong());
        verify(travelPlanInvitationService, never()).regenerateInvitation(anyLong(), anyLong());
        verify(travelPlanInvitationService, never()).disableInvitation(anyLong(), anyLong());
    }

    @Test
    void anonymousInvitationPostsAreSentToLogin() throws Exception {
        mockMvc.perform(post("/travel-plans/42/invitations").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(travelPlanInvitationService, never()).createInvitation(anyLong(), anyLong());
    }

    // ── 미리보기 ────────────────────────────────────────────

    @Test
    void anAnonymousVisitorCanOpenTheInviteLink() throws Exception {
        when(travelPlanInvitationService.resolvePreview(null, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false)));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/invitation-preview"))
                .andExpect(model().attributeExists("travelPlanInvitePreview"))
                .andExpect(model().attributeDoesNotExist("travelPlanInviteInvalid"));
    }

    @Test
    void aLoggedInVisitorIsResolvedWithTheirOwnUserId() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false)));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/invitation-preview"));

        verify(travelPlanInvitationService).resolvePreview(7L, RAW_TOKEN);
    }

    @Test
    void someoneAlreadyInTheRoomGoesStraightToThePlanner() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(true)));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN).with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"));
    }

    @Test
    void aDeadLinkGetsTheSameNoticeInsteadOfAnErrorPage() throws Exception {
        when(travelPlanInvitationService.resolvePreview(nullable(Long.class), anyString()))
                .thenReturn(Optional.empty());

        // REPLACED / DISABLED / 없는 토큰이 서로 구분되지 않고 같은 화면으로 끝난다
        for (String token : new String[]{"replacedToken", "disabledToken", "unknownToken"}) {
            mockMvc.perform(get("/travel-plans/invitations/" + token))
                    .andExpect(status().isOk())
                    .andExpect(view().name("travelplan/invitation-preview"))
                    .andExpect(model().attribute("travelPlanInviteInvalid", true))
                    .andExpect(model().attributeDoesNotExist("travelPlanInvitePreview"));
        }
    }

    @Test
    void aMalformedTokenEndsInTheSameNoticeRatherThanAnError() throws Exception {
        when(travelPlanInvitationService.resolvePreview(nullable(Long.class), anyString()))
                .thenReturn(Optional.empty());

        // 실제 토큰은 URL-safe Base64 라 이런 글자가 없다. 그래도 500 이 아니라 안내로 끝난다
        for (String malformed : new String[]{"not.a.token", "token~x", "AAAA"}) {
            mockMvc.perform(get("/travel-plans/invitations/" + malformed).with(user(member())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("travelplan/invitation-preview"))
                    .andExpect(model().attribute("travelPlanInviteInvalid", true));
        }
    }

    // ── 참여 ────────────────────────────────────────────────

    @Test
    void anAnonymousVisitorClickingJoinIsSentToLoginAndKeepsTheInviteUrl() throws Exception {
        // /join 은 인증이 필요한 GET 이라 로그인으로 보내지는데,
        // 토큰이 그대로 실려 로그인 성공 후 이 참여 흐름으로 되돌아온다.
        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/invitations/"
                        + RAW_TOKEN + "/join"));

        verify(travelPlanInvitationService, never())
                .resolvePreview(nullable(Long.class), anyString());
    }

    @Test
    void aLoggedInVisitorGetsTheNameFormWithTheRoomStillOnScreen() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false)));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/invitation-preview"))
                .andExpect(model().attributeExists("travelPlanJoinForm"))
                .andExpect(model().attribute("travelPlanInviteToken", RAW_TOKEN))
                .andExpect(model().attributeExists("travelPlanInvitePreview"));
    }

    @Test
    void aFullRoomOrABlockedVisitorGetsNoNameForm() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false, 8, false)))
                .thenReturn(Optional.of(preview(false, 3, true)));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                            .with(user(member())))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeDoesNotExist("travelPlanJoinForm"))
                    .andExpect(model().attributeExists("travelPlanInvitePreview"));
        }
    }

    @Test
    void aReturningMemberIsNotAskedForANameAgain() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(rejoinPreview()));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/invitation-preview"))
                // 이름 입력 폼 대신 재참여 확인만 보여 준다
                .andExpect(model().attributeDoesNotExist("travelPlanJoinForm"))
                .andExpect(model().attribute("travelPlanJoinScreen", true))
                .andExpect(model().attributeExists("travelPlanInvitePreview"));
    }

    @Test
    void aReturningMemberRejoinsThroughThePostWithoutSendingAName() throws Exception {
        when(travelPlanInvitationService.join(7L, RAW_TOKEN, null)).thenReturn(42L);

        // 상태 변경은 GET 이 아니라 POST 에서만 일어난다
        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"))
                .andExpect(flash().attribute("travelPlanMessage", "여행 계획에 참여했어요."));

        verify(travelPlanInvitationService).join(7L, RAW_TOKEN, null);
    }

    @Test
    void someoneAlreadyInTheRoomSkipsTheNameForm() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(true)));

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"));
    }

    @Test
    void aDeadLinkOnTheJoinScreenGetsTheSameNotice() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("travelPlanInviteInvalid", true))
                .andExpect(model().attributeDoesNotExist("travelPlanJoinForm"));
    }

    @Test
    void joiningPassesOnlyTheNameAndSendsTheUserIntoTheRoom() throws Exception {
        when(travelPlanInvitationService.join(7L, RAW_TOKEN, "예진")).thenReturn(42L);

        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())).with(csrf())
                        .param("displayName", "예진"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42"))
                .andExpect(flash().attribute("travelPlanMessage", "여행 계획에 참여했어요."));

        // 사용자는 로그인 정보에서, 방과 초대는 URL 토큰에서 온다
        verify(travelPlanInvitationService).join(7L, RAW_TOKEN, "예진");
    }

    @Test
    void aRefusedJoinRedrawsTheFormWithTheReasonInsteadOfAnErrorPage() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false)));
        when(travelPlanInvitationService.join(anyLong(), anyString(), anyString()))
                .thenThrow(new TravelPlanValidationException("displayName", "이미 사용 중인 이름입니다."));

        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())).with(csrf())
                        .param("displayName", "민준"))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/invitation-preview"))
                .andExpect(model().attribute("travelPlanError", "이미 사용 중인 이름입니다."))
                .andExpect(model().attributeExists("travelPlanJoinForm"));
    }

    @Test
    void aFullRoomRefusalIsShownOnTheSameScreen() throws Exception {
        when(travelPlanInvitationService.resolvePreview(7L, RAW_TOKEN))
                .thenReturn(Optional.of(preview(false, 8, false)));
        when(travelPlanInvitationService.join(anyLong(), anyString(), anyString()))
                .thenThrow(new TravelPlanValidationException("capacity", "참여 인원이 모두 찼어요."));

        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())).with(csrf())
                        .param("displayName", "예진"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("travelPlanError", "참여 인원이 모두 찼어요."));
    }

    @Test
    void joiningWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(user(member())).param("displayName", "예진"))
                .andExpect(status().isForbidden());

        verify(travelPlanInvitationService, never())
                .join(anyLong(), anyString(), anyString());
    }

    @Test
    void anAnonymousJoinPostNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/travel-plans/invitations/" + RAW_TOKEN + "/join")
                        .with(csrf()).param("displayName", "예진"))
                .andExpect(status().is3xxRedirection());

        verify(travelPlanInvitationService, never())
                .join(anyLong(), anyString(), anyString());
    }

    private TravelPlanInvitePreviewDto preview(boolean alreadyMember) {
        return preview(alreadyMember, 3, false);
    }

    private TravelPlanInvitePreviewDto preview(boolean alreadyMember, int memberCount,
                                               boolean joinBlocked) {
        return new TravelPlanInvitePreviewDto(42L, "제주도 여행",
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15),
                null, memberCount, 8, "민준", alreadyMember, joinBlocked, false, null);
    }

    /** 스스로 나갔던 사람이 다시 들어올 수 있는 상태. */
    private TravelPlanInvitePreviewDto rejoinPreview() {
        return new TravelPlanInvitePreviewDto(42L, "제주도 여행",
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15),
                null, 2, 8, "민준", false, false, true, "쭈니");
    }

    private CustomUserDetails member() {
        User user = new User();
        user.setId(7L);
        user.setUsername("minjun");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }
}
