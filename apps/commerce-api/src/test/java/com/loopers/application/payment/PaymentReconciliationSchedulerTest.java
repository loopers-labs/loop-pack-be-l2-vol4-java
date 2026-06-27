package com.loopers.application.payment;

import com.loopers.domain.money.Money;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentFacade paymentFacade;
    @InjectMocks
    private PaymentReconciliationScheduler scheduler;

    private Payment pendingPayment() {
        return new Payment(1L, 1L, "tx-001",
            new Money(BigDecimal.valueOf(1000)), PaymentStatus.PENDING, null);
    }

    @DisplayName("유예 시간이 지난 PENDING 결제를 PG에 조회한 상태로 confirm 한다.")
    @Test
    void confirmsStalePending_withPgStatus() {
        // arrange
        given(paymentRepository.findStalePending(any())).willReturn(List.of(pendingPayment()));
        given(paymentGateway.getTransaction("1", "tx-001"))
            .willReturn(new PaymentGateway.PaymentResult("tx-001", PaymentStatus.SUCCESS, null));

        // act
        scheduler.reconcile();

        // assert
        verify(paymentFacade).confirm("tx-001", PaymentStatus.SUCCESS, null);
    }

    @DisplayName("PG 조회가 실패하면 그 건은 건너뛰고 예외를 전파하지 않는다.")
    @Test
    void skipsItem_whenGatewayThrows() {
        // arrange
        given(paymentRepository.findStalePending(any())).willReturn(List.of(pendingPayment()));
        given(paymentGateway.getTransaction(any(), any())).willThrow(new RuntimeException("PG 조회 실패"));

        // act — 예외가 새어 나오지 않아야 한다
        scheduler.reconcile();

        // assert
        verify(paymentFacade, never()).confirm(any(), any(), any());
    }
}
