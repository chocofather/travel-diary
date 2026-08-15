package com.example.travlediary.service.user;

import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.UserAppeal;
import com.example.travlediary.repository.user.UserAppealMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAppealServiceTest {

    @Mock
    private UserAppealMapper userAppealMapper;
    @Mock
    private UserSanctionService userSanctionService;

    private AdminAppealService service;

    @BeforeEach
    void setUp() {
        service = new AdminAppealService(userAppealMapper, userSanctionService);
    }

    /* === 승인 === */

    @Test
    void approvalReusesTheSanctionReleaseAndClosesTheAppeal() {
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(pendingAppeal());
        when(userAppealMapper.handle(eq(30L), eq(AppealStatus.APPROVED), eq(1L),
                eq("소명 인정"), any(LocalDateTime.class))).thenReturn(1);

        service.approve(30L, "  소명 인정  ", 1L);

        // 해제·영구제재 차단 해제·상태 복원은 기존 제재 서비스가 담당한다
        verify(userSanctionService).releaseByAppeal(5L, 10L, "소명 인정", 1L);
        verify(userAppealMapper).handle(eq(30L), eq(AppealStatus.APPROVED), eq(1L),
                eq("소명 인정"), any(LocalDateTime.class));
    }

    @Test
    void approvalStopsWhenTheSanctionIsAlreadyLiftedOrBelongsToAnotherMember() {
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(pendingAppeal());
        doThrow(new SanctionValidationException(null, "이미 해제되었거나 만료된 이용제한입니다."))
                .when(userSanctionService).releaseByAppeal(5L, 10L, "소명 인정", 1L);

        assertThatThrownBy(() -> service.approve(30L, "소명 인정", 1L))
                .isInstanceOf(SanctionValidationException.class);

        verify(userAppealMapper, never()).handle(anyLong(), any(), anyLong(), anyString(), any());
    }

    /* === 기각 === */

    @Test
    void rejectionKeepsTheSanctionAndOnlyClosesTheAppeal() {
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(pendingAppeal());
        when(userAppealMapper.handle(eq(30L), eq(AppealStatus.REJECTED), eq(1L),
                eq("사유 불충분"), any(LocalDateTime.class))).thenReturn(1);

        service.reject(30L, "사유 불충분", 1L);

        verify(userSanctionService, never()).releaseByAppeal(anyLong(), anyLong(), any(), anyLong());
        verify(userAppealMapper).handle(eq(30L), eq(AppealStatus.REJECTED), eq(1L),
                eq("사유 불충분"), any(LocalDateTime.class));
    }

    /* === 공통 규칙 === */

    @Test
    void onlyPendingAppealsCanBeHandled() {
        UserAppeal handled = pendingAppeal();
        handled.setStatus(AppealStatus.APPROVED);
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(handled);

        assertThatThrownBy(() -> service.approve(30L, "사유", 1L))
                .isInstanceOf(AppealValidationException.class)
                .hasMessage("이미 처리된 이의제기입니다.");
        assertThatThrownBy(() -> service.reject(30L, "사유", 1L))
                .isInstanceOf(AppealValidationException.class);

        verify(userAppealMapper, never()).handle(anyLong(), any(), anyLong(), anyString(), any());
    }

    @Test
    void concurrentHandlingIsRejectedByTheStatusGuard() {
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(pendingAppeal());
        when(userAppealMapper.handle(eq(30L), eq(AppealStatus.REJECTED), eq(1L),
                eq("사유"), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.reject(30L, "사유", 1L))
                .isInstanceOf(AppealValidationException.class)
                .hasMessage("이미 처리된 이의제기입니다.");
    }

    @Test
    void adminReplyIsRequiredForBothOutcomes() {
        when(userAppealMapper.findByIdForUpdate(30L)).thenReturn(pendingAppeal());

        assertThatThrownBy(() -> service.approve(30L, "   ", 1L))
                .isInstanceOfSatisfying(AppealValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("adminReply"));
        assertThatThrownBy(() -> service.reject(30L, null, 1L))
                .isInstanceOfSatisfying(AppealValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("adminReply"));

        verify(userSanctionService, never()).releaseByAppeal(anyLong(), anyLong(), any(), anyLong());
        verify(userAppealMapper, never()).handle(anyLong(), any(), anyLong(), anyString(), any());
    }

    @Test
    void missingAppealReturnsNotFound() {
        when(userAppealMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.approve(99L, "사유", 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pendingCountUsesThePendingFilter() {
        when(userAppealMapper.countAdminAppeals(AppealStatus.PENDING, null)).thenReturn(3L);

        assertThat(service.countPendingAppeals()).isEqualTo(3L);
    }

    @Test
    void handlingRunsInOneWriteTransaction() throws NoSuchMethodException {
        for (String name : new String[]{"approve", "reject"}) {
            Transactional transactional = AdminAppealService.class
                    .getMethod(name, Long.class, String.class, Long.class)
                    .getAnnotation(Transactional.class);
            assertThat(transactional).as(name).isNotNull();
            assertThat(transactional.readOnly()).isFalse();
        }
    }

    private UserAppeal pendingAppeal() {
        UserAppeal appeal = new UserAppeal();
        appeal.setId(30L);
        appeal.setSanctionId(10L);
        appeal.setUserId(5L);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setContent("소명합니다");
        appeal.setSubmittedAt(LocalDateTime.of(2026, 8, 15, 12, 0));
        return appeal;
    }
}
