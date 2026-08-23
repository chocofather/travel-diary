package com.example.travlediary.controller.travelplan;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

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
                .andExpect(redirectedUrl("/travel-plans/new"))
                .andExpect(flash().attribute("travelPlanMessage", "공동 여행계획이 만들어졌어요."))
                .andExpect(flash().attribute("createdTravelPlanId", 42L));

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

    private CustomUserDetails member() {
        User user = new User();
        user.setId(7L);
        user.setUsername("minjun");
        user.setUserPassword("password");
        user.setUserRole(UserRole.USER);
        return new CustomUserDetails(user);
    }
}
