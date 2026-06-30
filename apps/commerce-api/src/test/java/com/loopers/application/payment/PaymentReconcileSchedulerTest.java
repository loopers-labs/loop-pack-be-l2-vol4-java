package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconcileSchedulerTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentReconciler reconciler = mock(PaymentReconciler.class);
    private final PaymentReconcileScheduler scheduler =
        new PaymentReconcileScheduler(paymentRepository, reconciler);

    @DisplayName("기한 초과 PENDING 결제들을 모두 reconcile 한다.")
    @Test
    void reconcilesAllOverduePending() {
        // arrange
        Payment p1 = mock(Payment.class);
        Payment p2 = mock(Payment.class);
        when(paymentRepository.findPendingOlderThan(any(ZonedDateTime.class))).thenReturn(List.of(p1, p2));

        // act
        scheduler.reconcilePending();

        // assert
        verify(reconciler).reconcile(p1);
        verify(reconciler).reconcile(p2);
    }

    @DisplayName("한 건 reconcile 이 실패해도, 나머지는 계속 진행한다.")
    @Test
    void continuesOnError() {
        // arrange
        Payment p1 = mock(Payment.class);
        Payment p2 = mock(Payment.class);
        when(paymentRepository.findPendingOlderThan(any(ZonedDateTime.class))).thenReturn(List.of(p1, p2));
        doThrow(new RuntimeException("boom")).when(reconciler).reconcile(p1);

        // act
        scheduler.reconcilePending();

        // assert
        verify(reconciler).reconcile(p2);
    }
}
