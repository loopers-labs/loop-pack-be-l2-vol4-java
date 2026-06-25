package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconcilerTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_NUMBER = "20260625-000033";
    private static final long AMOUNT = 29_000L;
    private static final String TRANSACTION_KEY = "20260625:TR:7466dd";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentResultHandler paymentResultHandler = mock(PaymentResultHandler.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentReconciler reconciler =
            new PaymentReconciler(paymentRepository, paymentGateway, paymentResultHandler, paymentService);

    private Payment stalePaymentWithKey() {
        Payment payment = Payment.create(USER_ID, ORDER_NUMBER, Money.of(AMOUNT));
        payment.assignTransaction(TRANSACTION_KEY, PgProvider.TOSS);
        return payment;
    }

    @Test
    @DisplayName("PG 조회가 terminal 이면 공유 진입점으로 확정한다")
    void givenPgTerminal_whenReconcile_thenConfirmsViaHandler() {
        when(paymentRepository.findStalePendingWithKey(any())).thenReturn(List.of(stalePaymentWithKey()));
        when(paymentGateway.inquire(USER_ID, TRANSACTION_KEY))
                .thenReturn(new PaymentGatewayResult(TRANSACTION_KEY, PgProvider.TOSS, PaymentStatus.SUCCESS, null));

        reconciler.reconcileWithKey();

        verify(paymentResultHandler).handle(argThat(c ->
                c.transactionKey().equals(TRANSACTION_KEY)
                        && c.orderNumber().equals(ORDER_NUMBER)
                        && c.amount() == AMOUNT
                        && c.status() == PaymentStatus.SUCCESS));
    }

    @Test
    @DisplayName("PG 조회가 아직 PENDING 이면 확정하지 않는다")
    void givenPgStillPending_whenReconcile_thenDoesNotConfirm() {
        when(paymentRepository.findStalePendingWithKey(any())).thenReturn(List.of(stalePaymentWithKey()));
        when(paymentGateway.inquire(eq(USER_ID), eq(TRANSACTION_KEY)))
                .thenReturn(new PaymentGatewayResult(TRANSACTION_KEY, PgProvider.TOSS, PaymentStatus.PENDING, null));

        reconciler.reconcileWithKey();

        verify(paymentResultHandler, never()).handle(any());
    }

    @Test
    @DisplayName("조회 실패 + 임계 시간 미경과면 FAILED·포기 없이 건너뛴다(다음 주기 재시도)")
    void givenInquiryFailsButNotOldEnough_whenReconcile_thenSkips() {
        Payment recent = stalePaymentWithKey();
        ReflectionTestUtils.setField(recent, "createdAt", ZonedDateTime.now());
        when(paymentRepository.findStalePendingWithKey(any())).thenReturn(List.of(recent));
        when(paymentGateway.inquire(any(), any())).thenThrow(new RuntimeException("PG 조회 실패"));

        reconciler.reconcileWithKey();

        verify(paymentResultHandler, never()).handle(any());
        verify(paymentService, never()).abandon(any(), anyString());
    }

    @Test
    @DisplayName("조회 실패 + 임계 시간 경과면 ABANDONED 로 포기한다(수동 확인 대상)")
    void givenInquiryFailsAndOldEnough_whenReconcile_thenAbandons() {
        Payment old = stalePaymentWithKey();
        ReflectionTestUtils.setField(old, "createdAt", ZonedDateTime.now().minusHours(2));
        when(paymentRepository.findStalePendingWithKey(any())).thenReturn(List.of(old));
        when(paymentGateway.inquire(any(), any())).thenThrow(new RuntimeException("PG 404"));

        reconciler.reconcileWithKey();

        verify(paymentService).abandon(any(), anyString());
        verify(paymentResultHandler, never()).handle(any());
    }
}
