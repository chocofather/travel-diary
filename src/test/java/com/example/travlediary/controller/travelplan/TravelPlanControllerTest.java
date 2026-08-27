package com.example.travlediary.controller.travelplan;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.TravelPlanDayDetailDto;
import com.example.travlediary.dto.TravelPlanDetailDto;
import com.example.travlediary.dto.TravelPlanListItemDto;
import com.example.travlediary.dto.TravelPlanMemberDto;
import com.example.travlediary.model.TravelPlan;
import com.example.travlediary.model.TravelPlanDay;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanRole;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanAccessNotice;
import com.example.travlediary.service.travelplan.TravelPlanConflictException;
import com.example.travlediary.service.travelplan.TravelPlanInvitationService;
import com.example.travlediary.service.travelplan.TravelPlanService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 방 생성 폼과 처리. 검증은 Service 가 하고 Controller 는 바인딩과 오류 표시만 맡는다.
 */
@WebMvcTest(TravelPlanController.class)
@Import(SecurityConfig.class)
class TravelPlanControllerTest {

    private static final String START = "2026-09-01";
    private static final String END = "2026-09-03";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelPlanService travelPlanService;
    @MockitoBean
    private TravelPlanInvitationService travelPlanInvitationService;
    /** 완료된 여행은 최종본에서만 읽는다. */
    @MockitoBean
    private com.example.travlediary.service.travelplan.TravelPlanFinalReadService
            travelPlanFinalReadService;
    /** 완료된 여행 지우기. 마지막 한 사람이 지우면 그 여행 자체가 사라진다. */
    @MockitoBean
    private com.example.travlediary.service.travelplan.TravelPlanFinalDeleteService
            travelPlanFinalDeleteService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void loggedInUserGetsAnEmptyCreateForm() throws Exception {
        mockMvc.perform(get("/travel-plans/new").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/create"))
                .andExpect(model().attributeExists("travelPlanCreateForm"))
                .andExpect(model().attribute("travelPlanCreateForm",
                        org.hamcrest.Matchers.hasProperty("title",
                                org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void anonymousAccessIsSentToLogin() throws Exception {
        // 별도 인가 규칙 없이 기존 anyRequest().authenticated() 정책을 그대로 탄다
        mockMvc.perform(get("/travel-plans/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/new"));
    }

    @Test
    void aValidSubmissionReachesTheServiceAndRedirectsWithAMessage() throws Exception {
        when(travelPlanService.createPlan(anyLong(), anyString(), any(), any(), anyString()))
                .thenReturn(42L);

        mockMvc.perform(post("/travel-plans")
                        .with(user(member())).with(csrf())
                        .param("title", "제주 여행")
                        .param("startDate", START)
                        .param("endDate", END)
                        .param("displayName", "민준"))
                .andExpect(status().is3xxRedirection())
                // 상세 화면이 생겼으므로 생성된 방으로 바로 보낸다
                .andExpect(redirectedUrl("/travel-plans/42"))
                .andExpect(flash().attribute("travelPlanMessage", "공동 여행계획이 만들어졌어요."));

        // 로그인 사용자 id 와 폼 값이 그대로 전달된다
        verify(travelPlanService).createPlan(
                eq(7L), eq("제주 여행"),
                eq(LocalDate.parse(START)), eq(LocalDate.parse(END)),
                eq("민준"));
    }

    @Test
    void aTitleValidationFailureIsShownOnTheTitleField() throws Exception {
        doThrow(new TravelPlanValidationException("title", "여행계획 이름을 입력해 주세요."))
                .when(travelPlanService).createPlan(anyLong(), any(), any(), any(), any());

        mockMvc.perform(post("/travel-plans")
                        .with(user(member())).with(csrf())
                        .param("title", "   ")
                        .param("startDate", START)
                        .param("endDate", END)
                        .param("displayName", "민준"))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/create"))
                .andExpect(model().attributeHasFieldErrors("travelPlanCreateForm", "title"))
                // 사용자가 입력한 나머지 값은 그대로 남는다
                .andExpect(model().attribute("travelPlanCreateForm",
                        org.hamcrest.Matchers.hasProperty("displayName",
                                org.hamcrest.Matchers.equalTo("민준"))))
                .andExpect(model().attribute("travelPlanCreateForm",
                        org.hamcrest.Matchers.hasProperty("startDate",
                                org.hamcrest.Matchers.equalTo(LocalDate.parse(START)))));
    }

    @Test
    void aDisplayNameValidationFailureIsShownOnItsOwnField() throws Exception {
        doThrow(new TravelPlanValidationException(
                "displayName", "이 방에서 사용할 표시 이름을 입력해 주세요."))
                .when(travelPlanService).createPlan(anyLong(), any(), any(), any(), any());

        mockMvc.perform(post("/travel-plans")
                        .with(user(member())).with(csrf())
                        .param("title", "제주 여행")
                        .param("startDate", START)
                        .param("endDate", END)
                        .param("displayName", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("travelPlanCreateForm", "displayName"));
    }

    @Test
    void periodValidationFailuresAreShownOnTheEndDateField() throws Exception {
        doThrow(new TravelPlanValidationException(
                "endDate", "여행 기간은 최대 90일까지 설정할 수 있습니다."))
                .when(travelPlanService).createPlan(anyLong(), any(), any(), any(), any());

        mockMvc.perform(post("/travel-plans")
                        .with(user(member())).with(csrf())
                        .param("title", "너무 긴 여행")
                        .param("startDate", START)
                        .param("endDate", "2027-09-01")
                        .param("displayName", "민준"))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/create"))
                .andExpect(model().attributeHasFieldErrors("travelPlanCreateForm", "endDate"));
    }

    @Test
    void aBrokenDateNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/travel-plans")
                        .with(user(member())).with(csrf())
                        .param("title", "제주 여행")
                        .param("startDate", "날짜아님")
                        .param("endDate", END)
                        .param("displayName", "민준"))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/create"))
                .andExpect(model().attributeHasFieldErrors("travelPlanCreateForm", "startDate"));

        verify(travelPlanService, never()).createPlan(anyLong(), any(), any(), any(), any());
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/travel-plans")
                        .with(user(member()))
                        .param("title", "제주 여행")
                        .param("startDate", START)
                        .param("endDate", END)
                        .param("displayName", "민준"))
                .andExpect(status().isForbidden());

        verify(travelPlanService, never()).createPlan(anyLong(), any(), any(), any(), any());
    }

    @Test
    void theListShowsOnlyTheCurrentUsersRooms() throws Exception {
        TravelPlanListItemDto row = new TravelPlanListItemDto();
        row.setTravelPlanId(42L);
        row.setTitle("제주 여행");
        row.setStartDate(LocalDate.parse(START));
        row.setEndDate(LocalDate.parse(END));
        row.setRole(TravelPlanRole.OWNER);
        row.setMemberCount(1);
        when(travelPlanService.getActivePlans(7L)).thenReturn(List.of(row));

        mockMvc.perform(get("/travel-plans").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/list"))
                .andExpect(model().attribute("travelPlans", List.of(row)));

        verify(travelPlanService).getActivePlans(7L);
    }

    @Test
    void anonymousListAccessIsSentToLogin() throws Exception {
        mockMvc.perform(get("/travel-plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans"));
    }

    @Test
    void theDetailPageAsksTheServiceWithTheCurrentUser() throws Exception {
        TravelPlan plan = new TravelPlan();
        plan.setId(42L);
        plan.setTitle("제주 여행");
        plan.setStartDate(LocalDate.parse(START));
        plan.setEndDate(LocalDate.parse(END));
        TravelPlanDetailDto detail = new TravelPlanDetailDto(
                plan, new TravelPlanMember(), List.of(), Map.of(), Map.of(),
                List.of(), List.of(), 8);
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(detail);

        mockMvc.perform(get("/travel-plans/42").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/detail"))
                .andExpect(model().attribute("travelPlan", detail));

        // planId 만으로 조회하지 않고 항상 로그인 사용자와 함께 넘긴다
        verify(travelPlanService).getActivePlanDetail(7L, 42L);
    }

    @Test
    void thePlannerOnlyLearnsWhetherAnInviteLinkIsOnNotItsUrl() throws Exception {
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(planDetail());
        when(travelPlanInvitationService.hasActiveInvitation(7L, 42L)).thenReturn(true);

        mockMvc.perform(get("/travel-plans/42").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("travelPlanInviteActive", true))
                // raw token 은 DB 에서 되살릴 수 없으므로 링크 문자열은 실리지 않는다
                .andExpect(model().attributeDoesNotExist("travelPlanInviteUrl"));

        verify(travelPlanInvitationService).hasActiveInvitation(7L, 42L);
    }

    @Test
    void theDayFragmentGivesBackJustThatDayForAnActiveMember() throws Exception {
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(planDetail());

        mockMvc.perform(get("/travel-plans/42/days/100/fragment").with(user(member())))
                .andExpect(status().isOk())
                // 처음 그릴 때와 같은 fragment 를 쓴다
                .andExpect(view().name("travelplan/fragments/schedule-day :: scheduleDay("
                        + "plan=${plan}, days=${days}, day=${day}, dayItems=${dayItems},"
                        + " alternativesByItemId=${alternativesByItemId}, dayOpen=${dayOpen})"))
                .andExpect(model().attributeExists("plan", "days", "day", "alternativesByItemId"))
                // 실시간 갱신에서는 입력칸을 열어 두지 않는다
                .andExpect(model().attribute("dayOpen", false));

        // 접근 권한은 상세 화면과 똑같이 Service 가 확인한다
        verify(travelPlanService).getActivePlanDetail(7L, 42L);
    }

    @Test
    void aDayFragmentFromAnotherRoomComesBackAsNotFound() throws Exception {
        // 그 방의 DAY 목록에 없는 dayId 는 통과하지 못한다
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(planDetail());

        mockMvc.perform(get("/travel-plans/42/days/999/fragment").with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDayFragmentOfARoomTheUserCannotSeeComesBackAsNotFound() throws Exception {
        // 비참여자 / LEFT / REMOVED 는 상세 조회에서 이미 막힌다
        when(travelPlanService.getActivePlanDetail(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));

        mockMvc.perform(get("/travel-plans/42/days/100/fragment").with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theFragmentEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/travel-plans/42/days/100/fragment"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/travel-plans/42/schedule/fragment"))
                .andExpect(status().is3xxRedirection());

        verify(travelPlanService, never()).getActivePlanDetail(anyLong(), anyLong());
    }

    @Test
    void theWholeScheduleFragmentIsOneRequestForReconnects() throws Exception {
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(planDetail());

        mockMvc.perform(get("/travel-plans/42/schedule/fragment").with(user(member())))
                .andExpect(status().isOk())
                // DAY 수만큼 요청이 나가지 않도록 통째로 준다
                .andExpect(view().name("travelplan/fragments/schedule-day :: scheduleDays("
                        + "plan=${plan}, days=${days}, itemsByDayId=${itemsByDayId},"
                        + " alternativesByItemId=${alternativesByItemId})"))
                .andExpect(model().attributeExists("plan", "days", "itemsByDayId"));

        verify(travelPlanService).getActivePlanDetail(7L, 42L);
    }

    @Test
    void theOldEditingUrlLeadsStraightToTheFinishedTrip() throws Exception {
        /*
          완료된 여행에 함께했던 사람이라면 볼 것이 있다.
          목록에서 다시 찾게 하지 않고 최종본으로 바로 보낸다.
        */
        when(travelPlanService.getActivePlanDetail(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));
        when(travelPlanService.explainInaccessiblePlan(anyLong(), anyLong()))
                .thenReturn(TravelPlanAccessNotice.COMPLETED_PARTICIPANT);

        mockMvc.perform(get("/travel-plans/999").with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/999/final"));
    }

    @Test
    void someoneWhoLeftBeforeTheEndOnlyHearsThatItIsOver() throws Exception {
        when(travelPlanService.getActivePlanDetail(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));
        when(travelPlanService.explainInaccessiblePlan(anyLong(), anyLong()))
                .thenReturn(TravelPlanAccessNotice.COMPLETED_PAST);

        mockMvc.perform(get("/travel-plans/999").with(user(member())))
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice", "이미 종료된 여행 계획입니다."));
    }

    @Test
    void aStrangerIsNotToldWhetherTheRoomEvenExists() throws Exception {
        when(travelPlanService.getActivePlanDetail(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));
        when(travelPlanService.explainInaccessiblePlan(anyLong(), anyLong()))
                .thenReturn(TravelPlanAccessNotice.NO_ACCESS);

        mockMvc.perform(get("/travel-plans/999").with(user(member())))
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice", "접근할 수 없는 여행 계획입니다."));
    }

    @Test
    void anonymousDetailAccessIsSentToLogin() throws Exception {
        mockMvc.perform(get("/travel-plans/42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/42"));
    }

    @Test
    void theDayPageAsksTheServiceWithTheCurrentUserAndBothIds() throws Exception {
        when(travelPlanService.getActiveDayDetail(7L, 42L, 100L)).thenReturn(dayDetail());

        mockMvc.perform(get("/travel-plans/42/days/100").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/day-detail"))
                .andExpect(model().attributeExists("travelPlanDay"))
                .andExpect(model().attributeExists("travelPlanItemCreateForm"));

        verify(travelPlanService).getActiveDayDetail(7L, 42L, 100L);
    }

    @Test
    void aDayTheUserCannotOpenSendsThemBackTheSameWay() throws Exception {
        when(travelPlanService.getActiveDayDetail(anyLong(), anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));
        when(travelPlanService.explainInaccessiblePlan(anyLong(), anyLong()))
                .thenReturn(TravelPlanAccessNotice.COMPLETED_PAST);

        mockMvc.perform(get("/travel-plans/42/days/999").with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice", "이미 종료된 여행 계획입니다."));
    }

    // ── 완료된 여행 ─────────────────────────────────────────

    @Test
    void theFinishedTripIsShownReadOnly() throws Exception {
        when(travelPlanFinalReadService.getCompletedPlanDetail(7L, 42L))
                .thenReturn(finalDetail());

        mockMvc.perform(get("/travel-plans/42/final").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("travelplan/final-detail"))
                .andExpect(model().attributeExists("finalPlan"));
    }

    @Test
    void someoneWhoWasNotOnTheTripIsSentAwayWithoutDetail() throws Exception {
        when(travelPlanFinalReadService.getCompletedPlanDetail(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "완료된 여행을 찾을 수 없습니다."));

        mockMvc.perform(get("/travel-plans/42/final").with(user(member())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice", "접근할 수 없는 여행 계획입니다."));
    }

    @Test
    void anonymousFinalAccessIsSentToLogin() throws Exception {
        mockMvc.perform(get("/travel-plans/42/final"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/42/final"));
    }

    // ── 완료된 여행 개인 삭제 ────────────────────────────────

    @Test
    void clearingAFinishedTripSendsMeBackToTheList() throws Exception {
        mockMvc.perform(post("/travel-plans/42/final/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice",
                        "완료된 여행을 내 목록에서 삭제했습니다."));

        // 지우는 것은 언제나 부른 사람 자신의 것이다
        verify(travelPlanFinalDeleteService).deleteForMe(7L, 42L);
    }

    @Test
    void theScreenSaysTheSameThingWhetherOrNotItWasTheLastCopy() throws Exception {
        // 마지막 한 사람이어서 여행 자체가 사라져도 하는 말은 같다.
        // 남이 아직 보관 중인지는 알릴 일이 아니다
        when(travelPlanFinalDeleteService.deleteForMe(7L, 42L)).thenReturn(true);

        mockMvc.perform(post("/travel-plans/42/final/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(redirectedUrl("/travel-plans"))
                .andExpect(flash().attribute("travelPlanNotice",
                        "완료된 여행을 내 목록에서 삭제했습니다."));
    }

    @Test
    void clearingSomeoneElsesFinishedTripIsRefusedWithoutRevealingIt() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "완료된 여행을 찾을 수 없습니다."))
                .when(travelPlanFinalDeleteService).deleteForMe(anyLong(), anyLong());

        mockMvc.perform(post("/travel-plans/42/final/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans"))
                // 그 최종본이 있는지조차 알리지 않는다
                .andExpect(flash().attribute("travelPlanNotice", "접근할 수 없는 여행 계획입니다."));
    }

    @Test
    void clearingIsNeverDoneByFollowingALink() throws Exception {
        // GET 으로는 지워지지 않는다 (주소를 눌러 보거나 미리 읽는 것만으로 사라지면 안 된다)
        mockMvc.perform(get("/travel-plans/42/final/delete").with(user(member())))
                .andExpect(status().isMethodNotAllowed());
        verify(travelPlanFinalDeleteService, never()).deleteForMe(anyLong(), anyLong());
    }

    @Test
    void clearingWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(post("/travel-plans/42/final/delete").with(user(member())))
                .andExpect(status().isForbidden());
        verify(travelPlanFinalDeleteService, never()).deleteForMe(anyLong(), anyLong());
    }

    @Test
    void anonymousClearingIsSentToLogin() throws Exception {
        mockMvc.perform(post("/travel-plans/42/final/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(travelPlanFinalDeleteService, never()).deleteForMe(anyLong(), anyLong());
    }

    @Test
    void theListShowsFinishedTripsAlongsideTheRunningOnes() throws Exception {
        mockMvc.perform(get("/travel-plans").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("travelPlans"))
                .andExpect(model().attributeExists("completedTravelPlans"));
    }

    @Test
    void finishedTripsShowEvenWhenNothingIsRunning() throws Exception {
        // 진행 중인 방이 하나도 없어도 완료된 여행은 따로 나온다
        when(travelPlanService.getActivePlans(7L)).thenReturn(List.of());
        com.example.travlediary.dto.TravelPlanFinalListItemDto finished =
                new com.example.travlediary.dto.TravelPlanFinalListItemDto();
        finished.setTravelPlanId(42L);
        finished.setSnapshotId(900L);
        finished.setTitle("제주도 여행");
        when(travelPlanFinalReadService.getCompletedPlans(7L)).thenReturn(List.of(finished));

        mockMvc.perform(get("/travel-plans").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("travelPlans", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(model().attribute("completedTravelPlans",
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void theListAsksForTheFinishedTripsOfWhoeverIsLookingAtIt() throws Exception {
        // 로그인한 사람의 계정으로 찾는다. 이 번호가 최종 명단의 user_id 와 맞아야 목록이 나온다
        mockMvc.perform(get("/travel-plans").with(user(member())))
                .andExpect(status().isOk());

        verify(travelPlanFinalReadService).getCompletedPlans(7L);
    }

    private com.example.travlediary.dto.TravelPlanFinalDetailDto finalDetail() {
        com.example.travlediary.model.TravelPlanFinalSnapshot snapshot =
                new com.example.travlediary.model.TravelPlanFinalSnapshot();
        snapshot.setId(900L);
        snapshot.setTravelPlanId(42L);
        snapshot.setTitle("제주도 여행");
        snapshot.setStartDate(java.time.LocalDate.of(2026, 9, 13));
        snapshot.setEndDate(java.time.LocalDate.of(2026, 9, 15));
        return new com.example.travlediary.dto.TravelPlanFinalDetailDto(
                snapshot, java.util.List.of(), java.util.List.of(),
                java.util.Map.of(), java.util.Map.of());
    }

    @Test
    void anonymousDayAccessIsSentToLogin() throws Exception {
        mockMvc.perform(get("/travel-plans/42/days/100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/42/days/100"));
    }

    @Test
    void addingAnItemComesBackToThatDayOnTheMainPlanner() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items")
                        .with(user(member())).with(csrf())
                        .param("content", "오전 10시 경복궁 도착\n한복 빌리고 천천히 둘러보기"))
                .andExpect(status().is3xxRedirection())
                // DAY 상세가 아니라 메인 편집 화면의 그 DAY 자리로 돌아온다
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).addItem(
                7L, 42L, 100L, "오전 10시 경복궁 도착\n한복 빌리고 천천히 둘러보기");
    }

    @Test
    void aBlankItemRedisplaysThePlannerWithThatDaysEditorOpen() throws Exception {
        doThrow(new TravelPlanValidationException("content", "일정 내용을 입력해 주세요."))
                .when(travelPlanService).addItem(anyLong(), anyLong(), anyLong(), any());
        when(travelPlanService.getActivePlanDetail(7L, 42L)).thenReturn(planDetail());

        mockMvc.perform(post("/travel-plans/42/days/100/items")
                        .with(user(member())).with(csrf())
                        .param("content", "   "))
                .andExpect(status().isOk())
                // 오류 페이지가 아니라 같은 편집 화면을 다시 그린다
                .andExpect(view().name("travelplan/detail"))
                .andExpect(model().attributeHasFieldErrors("travelPlanItemCreateForm", "content"))
                // 작성 중이던 내용이 남고, 어느 DAY 입력칸을 열지 알려 준다
                .andExpect(model().attribute("travelPlanItemCreateForm",
                        org.hamcrest.Matchers.hasProperty("content",
                                org.hamcrest.Matchers.equalTo("   "))))
                .andExpect(model().attribute("openDayId", 100L))
                .andExpect(model().attributeExists("travelPlan"));
    }

    @Test
    void addingAnItemWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items")
                        .with(user(member()))
                        .param("content", "일정"))
                .andExpect(status().isForbidden());

        verify(travelPlanService, never()).addItem(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void editingAnItemPassesTheVersionAndComesBackToThatDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/update")
                        .with(user(member())).with(csrf())
                        .param("content", "고친 일정")
                        .param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"))
                .andExpect(flash().attributeCount(0));

        verify(travelPlanService).updateItem(7L, 42L, 100L, 500L, "고친 일정", 3);
    }

    @Test
    void aConflictOnEditIsShownAsAMessageInsteadOfAnErrorPage() throws Exception {
        doThrow(new TravelPlanConflictException())
                .when(travelPlanService).updateItem(anyLong(), anyLong(), anyLong(), anyLong(),
                        any(), any());

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/update")
                        .with(user(member())).with(csrf())
                        .param("content", "고친 일정")
                        .param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"))
                .andExpect(flash().attribute("travelPlanError",
                        org.hamcrest.Matchers.containsString("다른 변경이 먼저 반영")));
    }

    @Test
    void aBlankEditIsShownAsAMessageInsteadOfAnErrorPage() throws Exception {
        doThrow(new TravelPlanValidationException("content", "일정 내용을 입력해 주세요."))
                .when(travelPlanService).updateItem(anyLong(), anyLong(), anyLong(), anyLong(),
                        any(), any());

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/update")
                        .with(user(member())).with(csrf())
                        .param("content", "   ")
                        .param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("travelPlanError", "일정 내용을 입력해 주세요."));
    }

    @Test
    void deletingAnItemComesBackToThatDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).deleteItem(7L, 42L, 100L, 500L);
    }

    @Test
    void editingOrDeletingWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/update")
                        .with(user(member()))
                        .param("content", "고친 일정").param("version", "3"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/delete")
                        .with(user(member())))
                .andExpect(status().isForbidden());

        verify(travelPlanService, never())
                .updateItem(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
        verify(travelPlanService, never()).deleteItem(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void addingAnAlternativePassesBothFieldsAndComesBackToThatDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives")
                        .with(user(member())).with(csrf())
                        .param("conditionLabel", "비가 많이 올 때")
                        .param("content", "아쿠아플라넷 방문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).addAlternative(
                7L, 42L, 100L, 500L, "비가 많이 올 때", "아쿠아플라넷 방문");
    }

    @Test
    void aRejectedAlternativeIsShownAsAMessageInsteadOfAnErrorPage() throws Exception {
        doThrow(new TravelPlanValidationException("content", "대안은 일정마다 2개까지 추가할 수 있습니다."))
                .when(travelPlanService).addAlternative(
                        anyLong(), anyLong(), anyLong(), anyLong(), any(), any());

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives")
                        .with(user(member())).with(csrf())
                        .param("content", "세 번째"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"))
                .andExpect(flash().attribute("travelPlanError",
                        "대안은 일정마다 2개까지 추가할 수 있습니다."));
    }

    @Test
    void editingAnAlternativePassesItsOwnVersion() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives/900/update")
                        .with(user(member())).with(csrf())
                        .param("conditionLabel", "눈 올 때")
                        .param("content", "실내 박물관")
                        .param("version", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).updateAlternative(
                7L, 42L, 100L, 500L, 900L, "눈 올 때", "실내 박물관", 4);
    }

    @Test
    void aConflictOnAnAlternativeEditIsShownAsAMessage() throws Exception {
        doThrow(new TravelPlanConflictException())
                .when(travelPlanService).updateAlternative(anyLong(), anyLong(), anyLong(),
                        anyLong(), anyLong(), any(), any(), any());

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives/900/update")
                        .with(user(member())).with(csrf())
                        .param("content", "실내 박물관").param("version", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("travelPlanError"));
    }

    @Test
    void deletingAnAlternativeComesBackToThatDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives/900/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).deleteAlternative(7L, 42L, 100L, 500L, 900L);
    }

    @Test
    void theGroupDeleteIsItsOwnEndpoint() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/delete-group")
                        .with(user(member())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        // "일정만 삭제" 와 뜻이 갈리므로 서로 다른 서비스 호출이다
        verify(travelPlanService).deleteItemGroup(7L, 42L, 100L, 500L);
        verify(travelPlanService, never()).deleteItem(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void theAlternativeEndpointsAreRejectedWithoutCsrf() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives")
                        .with(user(member())).param("content", "아쿠아플라넷 방문"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives/900/update")
                        .with(user(member())).param("content", "실내 박물관").param("version", "4"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/alternatives/900/delete")
                        .with(user(member())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/delete-group")
                        .with(user(member())))
                .andExpect(status().isForbidden());

        verify(travelPlanService, never()).addAlternative(
                anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
        verify(travelPlanService, never()).updateAlternative(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(travelPlanService, never()).deleteAlternative(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
        verify(travelPlanService, never()).deleteItemGroup(
                anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void anItemFromAnotherRoomComesBackAsNotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."))
                .when(travelPlanService).deleteItem(anyLong(), anyLong(), anyLong(), anyLong());

        mockMvc.perform(post("/travel-plans/42/days/100/items/999/delete")
                        .with(user(member())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void movingAnItemUpOrDownComesBackToTheSameDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move-up")
                        .with(user(member())).with(csrf()).param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move-down")
                        .with(user(member())).with(csrf()).param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/travel-plans/42#day-100"));

        verify(travelPlanService).moveItemUp(7L, 42L, 100L, 500L, 3);
        verify(travelPlanService).moveItemDown(7L, 42L, 100L, 500L, 3);
    }

    @Test
    void movingToAnotherDayFollowsTheItemToItsNewDay() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move")
                        .with(user(member())).with(csrf())
                        .param("targetDayId", "200").param("version", "3"))
                .andExpect(status().is3xxRedirection())
                // 옮긴 일정이 보이도록 대상 DAY 로 돌아간다
                .andExpect(redirectedUrl("/travel-plans/42#day-200"));

        verify(travelPlanService).moveItemToDay(7L, 42L, 100L, 500L, 200L, 3);
    }

    @Test
    void aRejectedMoveStaysOnTheOriginalDayWithAMessage() throws Exception {
        doThrow(new TravelPlanValidationException("itemId", "이미 첫 번째 일정입니다."))
                .when(travelPlanService).moveItemUp(anyLong(), anyLong(), anyLong(), anyLong(), any());
        doThrow(new TravelPlanConflictException())
                .when(travelPlanService).moveItemToDay(anyLong(), anyLong(), anyLong(), anyLong(),
                        anyLong(), any());

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move-up")
                        .with(user(member())).with(csrf()).param("version", "3"))
                .andExpect(redirectedUrl("/travel-plans/42#day-100"))
                .andExpect(flash().attribute("travelPlanError", "이미 첫 번째 일정입니다."));

        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move")
                        .with(user(member())).with(csrf())
                        .param("targetDayId", "200").param("version", "3"))
                // 이동이 실패했으니 원래 DAY 에 남는다
                .andExpect(redirectedUrl("/travel-plans/42#day-100"))
                .andExpect(flash().attributeExists("travelPlanError"));
    }

    @Test
    void movingWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move-up")
                        .with(user(member())).param("version", "3"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move-down")
                        .with(user(member())).param("version", "3"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/travel-plans/42/days/100/items/500/move")
                        .with(user(member()))
                        .param("targetDayId", "200").param("version", "3"))
                .andExpect(status().isForbidden());

        verify(travelPlanService, never())
                .moveItemUp(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(travelPlanService, never())
                .moveItemDown(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(travelPlanService, never())
                .moveItemToDay(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    private TravelPlanDetailDto planDetail() {
        TravelPlan plan = new TravelPlan();
        plan.setId(42L);
        plan.setTitle("제주 여행");
        plan.setStartDate(LocalDate.parse(START));
        plan.setEndDate(LocalDate.parse(END));
        TravelPlanDay day = new TravelPlanDay();
        day.setId(100L);
        day.setTravelPlanId(42L);
        day.setDayNumber(1);
        day.setPlanDate(LocalDate.parse(START));
        return new TravelPlanDetailDto(
                plan, new TravelPlanMember(), List.of(day), Map.of(), Map.of(),
                List.of(new TravelPlanMemberDto(11L, "민준", TravelPlanRole.OWNER, true)),
                List.of(), 8);
    }

    private TravelPlanDayDetailDto dayDetail() {
        TravelPlan plan = new TravelPlan();
        plan.setId(42L);
        plan.setTitle("제주 여행");
        TravelPlanDay day = new TravelPlanDay();
        day.setId(100L);
        day.setTravelPlanId(42L);
        day.setDayNumber(1);
        day.setPlanDate(LocalDate.parse(START));
        return new TravelPlanDayDetailDto(plan, new TravelPlanMember(), day, List.of());
    }

    // ── 참여자 명단 조각 ─────────────────────────────────────

    @Test
    void theHeadcountComesFromTheServerNotFromTheScreen() throws Exception {
        when(travelPlanService.getActivePlanMembers(7L, 42L)).thenReturn(membersOf(
                new com.example.travlediary.dto.TravelPlanMemberDto(
                        11L, "민준", com.example.travlediary.model.TravelPlanRole.OWNER, true),
                new com.example.travlediary.dto.TravelPlanMemberDto(
                        12L, "쭈니", com.example.travlediary.model.TravelPlanRole.MEMBER, false)));

        mockMvc.perform(get("/travel-plans/42/members/fragment").with(user(member())))
                .andExpect(status().isOk())
                // 화면이 더하거나 빼지 않도록 토글 글자까지 서버가 만들어 보낸다
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-members-label=\"참여자 2/8\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("민준")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("쭈니")));
    }

    @Test
    void theMemberListFragmentIsNotAWholePage() throws Exception {
        // 패널 속만 갈아 끼우므로 껍데기가 딸려 오면 안 된다
        when(travelPlanService.getActivePlanMembers(7L, 42L)).thenReturn(membersOf(
                new com.example.travlediary.dto.TravelPlanMemberDto(
                        11L, "민준", com.example.travlediary.model.TravelPlanRole.OWNER, true)));

        mockMvc.perform(get("/travel-plans/42/members/fragment").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<html"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("travel-plan-members-panel"))));
    }

    @Test
    void someoneWhoIsNotInTheRoomCannotReadItsMemberList() throws Exception {
        when(travelPlanService.getActivePlanMembers(anyLong(), anyLong()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행계획을 찾을 수 없습니다."));

        mockMvc.perform(get("/travel-plans/42/members/fragment").with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousMemberListAccessIsSentToLogin() throws Exception {
        mockMvc.perform(get("/travel-plans/42/members/fragment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/travel-plans/42/members/fragment"));
    }

    private com.example.travlediary.dto.TravelPlanMembersDto membersOf(
            com.example.travlediary.dto.TravelPlanMemberDto... members) {
        com.example.travlediary.model.TravelPlan plan =
                new com.example.travlediary.model.TravelPlan();
        plan.setId(42L);
        com.example.travlediary.model.TravelPlanMember current =
                new com.example.travlediary.model.TravelPlanMember();
        current.setId(11L);
        current.setRole(com.example.travlediary.model.TravelPlanRole.OWNER);
        return new com.example.travlediary.dto.TravelPlanMembersDto(
                plan, current, List.of(members), List.of(), 8);
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
