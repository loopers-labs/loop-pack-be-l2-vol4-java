package com.loopers.infrastructure.payment.scheduler;

import com.loopers.application.payment.payment.PaymentWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWorkerSchedulerTest {

    @DisplayName("스케줄러가 실행되면 요청 결제 worker에 처리를 위임한다.")
    @Test
    void delegatesToPaymentWorker_whenScheduledMethodRuns() {
        // arrange
        PaymentWorker paymentWorker = mock(PaymentWorker.class);
        when(paymentWorker.processRequestedPayments()).thenReturn(List.of());
        PaymentWorkerScheduler scheduler = new PaymentWorkerScheduler(paymentWorker);

        // act
        scheduler.processRequestedPayments();

        // assert
        verify(paymentWorker).processRequestedPayments();
    }
}
