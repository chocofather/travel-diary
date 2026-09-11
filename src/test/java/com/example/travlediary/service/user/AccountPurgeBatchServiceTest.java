package com.example.travlediary.service.user;

import com.example.travlediary.model.AccountPurgeJobStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPurgeBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 3, 0);

    @Mock private UserMapper userMapper;
    @Mock private AccountPurgeTransactionService accountPurgeTransactionService;

    private AccountPurgeBatchService service;

    @BeforeEach
    void setUp() {
        service = new AccountPurgeBatchService(userMapper, accountPurgeTransactionService);
    }

    /** 전체 대상을 한 번에 읽지 않는다. */
    @Test
    void theBatchAsksForAtMostOnePageOfDueAccounts() {
        when(userMapper.findDueWithdrawalUserIds(NOW, AccountPurgeBatchService.BATCH_SIZE))
                .thenReturn(List.of());

        service.purgeDueAccounts(NOW);

        verify(userMapper).findDueWithdrawalUserIds(NOW, 100);
    }

    @Test
    void everyDueAccountIsPurgedInItsOwnCall() {
        when(userMapper.findDueWithdrawalUserIds(eq(NOW), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(accountPurgeTransactionService.purgeOne(org.mockito.ArgumentMatchers.anyLong(), eq(NOW)))
                .thenReturn(purged());

        assertThat(service.purgeDueAccounts(NOW)).isEqualTo(3);

        verify(accountPurgeTransactionService).purgeOne(1L, NOW);
        verify(accountPurgeTransactionService).purgeOne(2L, NOW);
        verify(accountPurgeTransactionService).purgeOne(3L, NOW);
    }

    /** 한 회원의 실패가 다음 회원 처리를 멈추면 안 된다. */
    @Test
    void oneFailingAccountDoesNotStopTheRest() {
        when(userMapper.findDueWithdrawalUserIds(eq(NOW), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(accountPurgeTransactionService.purgeOne(1L, NOW)).thenReturn(purged());
        when(accountPurgeTransactionService.purgeOne(2L, NOW))
                .thenThrow(new IllegalStateException("boom"));
        when(accountPurgeTransactionService.purgeOne(3L, NOW)).thenReturn(purged());

        assertThat(service.purgeDueAccounts(NOW)).isEqualTo(2);

        verify(accountPurgeTransactionService).purgeOne(3L, NOW);
    }

    /** 복구된 회원은 no-op 으로 돌아오고 파기 건수에 들어가지 않는다. */
    @Test
    void skippedAccountsAreNotCountedAsPurged() {
        when(userMapper.findDueWithdrawalUserIds(eq(NOW), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(accountPurgeTransactionService.purgeOne(1L, NOW))
                .thenReturn(AccountPurgeTransactionService.PurgeOutcome.skipped());
        when(accountPurgeTransactionService.purgeOne(2L, NOW)).thenReturn(purged());

        assertThat(service.purgeDueAccounts(NOW)).isEqualTo(1);
    }

    @Test
    void anEmptyDueListDoesNoWork() {
        when(userMapper.findDueWithdrawalUserIds(eq(NOW), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        assertThat(service.purgeDueAccounts(NOW)).isZero();

        org.mockito.Mockito.verifyNoInteractions(accountPurgeTransactionService);
    }

    /** 배치 자체는 트랜잭션을 열지 않는다. 경계는 회원별 purgeOne 이 갖는다. */
    @Test
    void theBatchLoopIsNotTransactional() throws Exception {
        assertThat(AccountPurgeBatchService.class.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class)).isNull();
        assertThat(AccountPurgeBatchService.class
                .getMethod("purgeDueAccounts", LocalDateTime.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
    }

    private AccountPurgeTransactionService.PurgeOutcome purged() {
        return new AccountPurgeTransactionService.PurgeOutcome(
                true, 100L, 0, AccountPurgeJobStatus.COMPLETED);
    }
}
