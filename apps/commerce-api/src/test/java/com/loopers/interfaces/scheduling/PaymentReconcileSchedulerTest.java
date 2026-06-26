package com.loopers.interfaces.scheduling;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentReconcileResult;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReconcileScheduler")
class PaymentReconcileSchedulerTest {

    @Mock
    PaymentFacade paymentFacade;

    @InjectMocks
    PaymentReconcileScheduler scheduler;

    // @SchedulerLock 프록시 없이 직접 호출하므로 LockAssert.assertLocked() 가 막지 않도록 통과 처리한다.
    @BeforeAll
    static void enableLockAssert() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterAll
    static void disableLockAssert() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    @DisplayName("설정된 page-size 로 첫 페이지 PENDING 을 reconcile 한다")
    void delegatesToFacadeWithConfiguredPageSize() {
        ReflectionTestUtils.setField(scheduler, "pageSize", 50);
        when(paymentFacade.reconcilePending(0, 50))
            .thenReturn(new PaymentReconcileResult(0, 0, 0, 0, 0, 0));

        scheduler.reconcilePending();

        verify(paymentFacade).reconcilePending(eq(0), eq(50));
    }
}
