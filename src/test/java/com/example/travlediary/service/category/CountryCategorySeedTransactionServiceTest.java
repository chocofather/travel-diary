package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기준 데이터 저장이 실제로 한 트랜잭션으로 묶이는지.
 *
 * <p>어노테이션이 붙어 있다는 것만으로는 부족하다. {@code @PostConstruct} 에서 부르는 구조라
 * 프록시를 지나지 않으면 아무 일도 하지 않기 때문이다. 그래서 진짜 Spring 컨텍스트에
 * {@code @EnableTransactionManagement} 를 켜고 트랜잭션 관리자를 가짜로 끼워,
 * 커밋과 롤백이 실제로 요청되는지를 본다.
 */
class CountryCategorySeedTransactionServiceTest {

    private AnnotationConfigApplicationContext context;
    private CountryCategorySeedTransactionService service;
    private CountryCategoryMapper mapper;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        mapper = mock(CountryCategoryMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        context = new AnnotationConfigApplicationContext();
        context.registerBean(CountryCategoryMapper.class, () -> mapper);
        context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        context.registerBean(CountryCategorySeedTransactionService.class,
                () -> new CountryCategorySeedTransactionService(mapper, new CountryCategoryCache()));
        context.register(TransactionConfig.class);
        context.refresh();

        service = context.getBean(CountryCategorySeedTransactionService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /** 경계가 실제로 걸려 있다. (프록시가 아니면 이 단언이 먼저 깨진다) */
    @Test
    void theServiceIsActuallyProxiedForTransactions() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(service)).isTrue();
    }

    /** 없는 행만 넣고, 끝나면 커밋을 요청한다. */
    @Test
    void onlyMissingRowsAreInsertedAndTheWorkIsCommitted() {
        when(mapper.selectAllIds()).thenReturn(List.of(1, 2));

        int inserted = service.insertMissing(List.of(
                category(1L), category(2L), category(3L), category(4L)));

        assertThat(inserted).isEqualTo(2);
        verify(mapper).insert(argThatHasId(3L));
        verify(mapper).insert(argThatHasId(4L));
        verify(mapper, never()).insert(argThatHasId(1L));
        verify(mapper, never()).insert(argThatHasId(2L));
        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    /** 이미 다 있으면 한 행도 넣지 않는다. */
    @Test
    void nothingIsInsertedWhenEveryRowAlreadyExists() {
        when(mapper.selectAllIds()).thenReturn(List.of(1, 2, 3));

        assertThat(service.insertMissing(List.of(category(1L), category(2L), category(3L))))
                .isZero();

        verify(mapper, never()).insert(any());
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    /**
     * 중간에 실패하면 되돌린다.
     *
     * <p>첫 행은 이미 insert 를 불렀지만 커밋은 요청되지 않고 롤백이 요청된다 —
     * 즉 절반만 들어간 상태로 커밋되지 않는다.
     */
    @Test
    void aFailureInTheMiddleRollsBackInsteadOfCommitting() {
        when(mapper.selectAllIds()).thenReturn(List.of());
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("insert failed"))
                .when(mapper).insert(any());

        assertThatThrownBy(() ->
                service.insertMissing(List.of(category(1L), category(2L), category(3L))))
                .isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        // 세 번째 행은 시도되지도 않는다
        verify(mapper, never()).insert(argThatHasId(3L));
    }

    /** 조회 단계에서 실패해도 커밋하지 않는다. */
    @Test
    void aLookupFailureAlsoRollsBack() {
        when(mapper.selectAllIds()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.insertMissing(List.of(category(1L))))
                .isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
    }

    /** 번호가 없는 항목은 기존 행과 맞춰 볼 수 없으므로 새 행으로 넣는다. */
    @Test
    void anEntryWithoutAnIdIsTreatedAsNew() {
        when(mapper.selectAllIds()).thenReturn(List.of(1));

        assertThat(service.insertMissing(List.of(category(1L), category(null)))).isEqualTo(1);
    }

    /* ===== 도우미 ===== */

    @EnableTransactionManagement
    static class TransactionConfig {
    }

    private CountryCategory argThatHasId(Long id) {
        return org.mockito.ArgumentMatchers.argThat(
                category -> category != null && java.util.Objects.equals(category.getId(), id));
    }

    private CountryCategory category(Long id) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setRegionName("지역" + id);
        category.setDepth(1);
        return category;
    }
}
