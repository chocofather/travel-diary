package com.example.travlediary.service.user;

import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.UserAppeal;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.repository.user.UserAppealMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAppealServiceTest {

    @Mock
    private UserSanctionMapper userSanctionMapper;
    @Mock
    private UserAppealMapper userAppealMapper;

    private UserAppealService service;

    @BeforeEach
    void setUp() {
        service = new UserAppealService(userSanctionMapper, userAppealMapper);
    }

    @Test
    void submissionUsesTheCurrentSanctionOfTheSignedInMember() {
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L))
                .thenReturn(sanction(SanctionType.TEMPORARY));
        when(userAppealMapper.insert(any(UserAppeal.class))).thenReturn(1);

        service.submit(5L, "  제재 사유에 오해가 있습니다.  ");

        ArgumentCaptor<UserAppeal> captor = ArgumentCaptor.forClass(UserAppeal.class);
        verify(userAppealMapper).insert(captor.capture());
        UserAppeal saved = captor.getValue();
        // 대상 제재는 입력값이 아니라 서버가 찾은 현재 제재다
        assertThat(saved.getSanctionId()).isEqualTo(10L);
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getStatus()).isEqualTo(AppealStatus.PENDING);
        assertThat(saved.getContent()).isEqualTo("제재 사유에 오해가 있습니다.");
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test
    void bothTemporaryAndPermanentSanctionsCanBeAppealed() {
        for (SanctionType type : SanctionType.values()) {
            when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(sanction(type));
            when(userAppealMapper.insert(any(UserAppeal.class))).thenReturn(1);

            service.submit(5L, "소명합니다");
        }
        verify(userAppealMapper, org.mockito.Mockito.times(SanctionType.values().length))
                .insert(any(UserAppeal.class));
    }

    @Test
    void contentIsRequired() {
        assertThatThrownBy(() -> service.submit(5L, "   "))
                .isInstanceOfSatisfying(AppealValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("content"));

        verify(userAppealMapper, never()).insert(any());
        verify(userSanctionMapper, never()).findActiveByUserIdForUpdate(anyLong());
    }

    @Test
    void appealIsRejectedWhenTheSanctionIsAlreadyLiftedOrExpired() {
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.submit(5L, "소명합니다"))
                .isInstanceOf(AppealValidationException.class)
                .hasMessage("이의제기할 이용제한이 없습니다.");

        verify(userAppealMapper, never()).insert(any());
    }

    @Test
    void duplicatePendingAppealsAreBlocked() {
        when(userSanctionMapper.findActiveByUserIdForUpdate(5L))
                .thenReturn(sanction(SanctionType.PERMANENT));
        when(userAppealMapper.findPendingBySanctionId(10L)).thenReturn(pendingAppeal());

        assertThatThrownBy(() -> service.submit(5L, "소명합니다"))
                .isInstanceOf(AppealValidationException.class)
                .hasMessage("이미 접수된 이의제기가 처리 중입니다.");

        verify(userAppealMapper, never()).insert(any());
    }

    @Test
    void anonymousSubmissionIsRejected() {
        assertThatThrownBy(() -> service.submit(null, "소명합니다"))
                .isInstanceOf(AppealValidationException.class);

        verify(userAppealMapper, never()).insert(any());
    }

    @Test
    void latestAppealLookupIsNullSafe() {
        assertThat(service.getLatestAppeal(null)).isNull();
        verify(userAppealMapper, never()).findLatestBySanctionId(anyLong());

        UserAppeal appeal = pendingAppeal();
        when(userAppealMapper.findLatestBySanctionId(10L)).thenReturn(appeal);
        assertThat(service.getLatestAppeal(10L)).isSameAs(appeal);
    }

    @Test
    void submissionRunsInOneWriteTransaction() throws NoSuchMethodException {
        Transactional transactional = UserAppealService.class
                .getMethod("submit", Long.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private UserSanction sanction(SanctionType type) {
        UserSanction sanction = new UserSanction();
        sanction.setId(10L);
        sanction.setUserId(5L);
        sanction.setType(type);
        sanction.setStatus(SanctionStatus.ACTIVE);
        sanction.setReason("이용약관 위반");
        if (type == SanctionType.TEMPORARY) {
            sanction.setExpiresAt(LocalDateTime.now().plusDays(3));
        }
        return sanction;
    }

    private UserAppeal pendingAppeal() {
        UserAppeal appeal = new UserAppeal();
        appeal.setId(30L);
        appeal.setSanctionId(10L);
        appeal.setUserId(5L);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setContent("이미 제출한 내용");
        appeal.setSubmittedAt(LocalDateTime.of(2026, 8, 15, 12, 0));
        return appeal;
    }
}
