package com.loopers.application.payment.payment;

import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWorkerTest {

    @DisplayName("결제 worker는 비관적 락 대상 조회로 REQUESTED 결제를 가져온다.")
    @Test
    void usesLockedRequestedPaymentQuery_whenProcessingPayments() {
        // arrange
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        Payment payment = new Payment(1L, 1_000L);
        when(paymentRepository.findRequestedPaymentsForUpdate(100)).thenReturn(List.of(payment));
        when(paymentService.processRequestedPayment(1L)).thenReturn(PaymentProcessResult.success(1L));
        PaymentWorker worker = new PaymentWorker(paymentRepository, paymentService);

        // act
        List<PaymentProcessResult> results = worker.processRequestedPayments();

        // assert
        assertThat(results).hasSize(1);
        verify(paymentRepository).findRequestedPaymentsForUpdate(100);
        verify(paymentService).processRequestedPayment(1L);
    }
}
