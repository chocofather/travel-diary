package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTranslationUsageReservationServiceTest {

    @Test
    void reservesDailyBeforeMonthlyAndPassesTheAuthenticatedSubject() {
        List<String> calls = new ArrayList<>();
        TranslationDailyUsageGate daily = (text, ip, userId) -> {
            assertThat(text).isEqualTo("원문");
            assertThat(ip).isEqualTo("203.0.113.9");
            assertThat(userId).isEqualTo(42L);
            calls.add("daily");
        };
        TranslationMonthlyUsageGate monthly = text -> calls.add("monthly");

        new GoogleTranslationUsageReservationService(daily, monthly)
                .reserve("원문", "203.0.113.9", 42L);

        assertThat(calls).containsExactly("daily", "monthly");
    }

    @Test
    void monthlyRejectionEscapesTheTransactionalBoundaryForDailyRollback() throws Exception {
        List<String> calls = new ArrayList<>();
        AtomicLong dailyCharacters = new AtomicLong();
        TranslationDailyUsageGate daily = (text, ip, userId) -> {
            calls.add("daily");
            dailyCharacters.addAndGet(text.codePointCount(0, text.length()));
        };
        TranslationMonthlyUsageGate monthly = text -> {
            calls.add("monthly");
            throw new TranslationMonthlyLimitException();
        };
        GoogleTranslationUsageReservationService target =
                new GoogleTranslationUsageReservationService(daily, monthly);
        TranslationUsageReservationGate service = transactionalProxy(target, dailyCharacters);

        assertThatThrownBy(() -> service.reserve("원문", "203.0.113.9", null))
                .isInstanceOf(TranslationMonthlyLimitException.class);
        assertThat(calls).containsExactly("daily", "monthly");
        assertThat(dailyCharacters).hasValue(0L);

        Method reserve = GoogleTranslationUsageReservationService.class.getMethod(
                "reserve", String.class, String.class, Long.class);
        assertThat(reserve.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private TranslationUsageReservationGate transactionalProxy(
            GoogleTranslationUsageReservationService target,
            AtomicLong transactionalState) {
        SnapshotTransactionManager transactionManager =
                new SnapshotTransactionManager(transactionalState);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        return (TranslationUsageReservationGate) proxyFactory.getProxy();
    }

    private static final class SnapshotTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicLong state;
        private final ThreadLocal<Long> snapshot = new ThreadLocal<>();

        private SnapshotTransactionManager(AtomicLong state) {
            this.state = state;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            snapshot.set(state.get());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            snapshot.remove();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            state.set(snapshot.get());
            snapshot.remove();
        }
    }
}
