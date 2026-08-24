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

    private TravelPlanInvitePreviewDto preview(boolean alreadyMember) {
        return new TravelPlanInvitePreviewDto(42L, "제주도 여행",
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15),
                null, 3, "민준", alreadyMember);
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
