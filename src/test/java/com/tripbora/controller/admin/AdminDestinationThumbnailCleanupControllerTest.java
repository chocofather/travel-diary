package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupPlan;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupResult;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupTarget;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.PlanChangedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDestinationThumbnailCleanupController.class)
@Import(SecurityConfig.class)
class AdminDestinationThumbnailCleanupControllerTest {

    private static final String PAGE = "/admin/maintenance/no-derivative-thumbnails";
    private static final CleanupPlan PLAN = new CleanupPlan(2, List.of(
            new CleanupTarget("v1", 480, "a.jpg", "thumbnail-cache/destinations/v1/480/a.jpg", 1000),
            new CleanupTarget("v2", 960, "a.jpg", "thumbnail-cache/destinations/v2/960/a.jpg", 2000)));

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DestinationThumbnailCacheCleanupService cleanupService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void onlyAdminsCanOpenTheMaintenancePage() throws Exception {
        mockMvc.perform(get(PAGE).with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        verify(cleanupService, never()).plan();
    }

    @Test
    void getShowsTheDryRunAndNeverDeletes() throws Exception {
        when(cleanupService.plan()).thenReturn(PLAN);

        mockMvc.perform(get(PAGE).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3유형 원본 2개 · 지울 파일 2개")))
                .andExpect(content().string(containsString("v1/480: 1개")))
                .andExpect(content().string(containsString("v2/960: 1개")))
                .andExpect(content().string(containsString("thumbnail-cache/destinations/v2/960/a.jpg")))
                .andExpect(content().string(containsString("name=\"expectedFileCount\" value=\"2\"")))
                .andExpect(content().string(containsString("name=\"expectedTotalBytes\" value=\"3000\"")));
        verify(cleanupService, never()).execute(anyInt(), anyLong());
    }

    @Test
    void emptyDryRunSaysThereIsNothingToClean() throws Exception {
        when(cleanupService.plan()).thenReturn(new CleanupPlan(0, List.of()));

        mockMvc.perform(get(PAGE).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("정리할 파일 없음")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("실제 삭제"))));
    }

    @Test
    void executeNeedsCsrfAndTheExplicitConfirmation() throws Exception {
        when(cleanupService.plan()).thenReturn(PLAN);

        mockMvc.perform(post(PAGE + "/execute").with(user("admin").roles("ADMIN"))
                        .param("expectedFileCount", "2").param("expectedTotalBytes", "3000").param("confirm", "DELETE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PAGE + "/execute").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("expectedFileCount", "2").param("expectedTotalBytes", "3000").param("confirm", "yes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아무것도 지우지 않았습니다")));
        verify(cleanupService, never()).execute(anyInt(), anyLong());
    }

    @Test
    void confirmedExecuteDeletesAndShowsTheVerification() throws Exception {
        when(cleanupService.execute(2, 3000)).thenReturn(
                new CleanupResult(PLAN, PLAN.targets(), List.of(), true, true));

        mockMvc.perform(post(PAGE + "/execute").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("expectedFileCount", "2").param("expectedTotalBytes", "3000").param("confirm", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삭제 2개")))
                .andExpect(content().string(containsString("대상 파일 모두 사라짐: 예 · 원본 그대로: 예")));
        verify(cleanupService).execute(2, 3000);
    }

    @Test
    void aChangedPlanDeletesNothingAndShowsTheNewDryRun() throws Exception {
        when(cleanupService.execute(2, 3000)).thenThrow(new PlanChangedException(new CleanupPlan(0, List.of())));

        mockMvc.perform(post(PAGE + "/execute").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("expectedFileCount", "2").param("expectedTotalBytes", "3000").param("confirm", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dry-run 이후 정리 대상이 바뀌었습니다")))
                .andExpect(content().string(containsString("정리할 파일 없음")));
    }
}
