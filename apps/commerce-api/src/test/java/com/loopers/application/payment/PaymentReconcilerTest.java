package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionDetail;
import com.loopers.domain.payment.PgTransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("domain")
class PaymentReconcilerTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentApplicationService paymentApplicationService = mock(PaymentApplicationService.class);
    private final PgClient pgClient = mock(PgClient.class);
    private final PaymentReconciler reconciler = new PaymentReconciler(paymentRepository, paymentApplicationService, pgClient);

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 7L;

    private static PaymentModel pending(String transactionKey) {
        return new PaymentModel(PAYMENT_ID, USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
            transactionKey, PaymentStatus.PENDING, null, null, null);
    }

    @Test
    @DisplayName("거래키 보유 PENDING: PG 가 SUCCESS 면 confirm 한다")
    void reconcileByTransactionKey() {
        // arrange
        PaymentModel payment = pending("TR:abc");
        when(pgClient.getTransaction("1", "TR:abc"))
            .thenReturn(Optional.of(new PgTransactionDetail("TR:abc", "000100", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, PgTransactionStatus.SUCCESS, null)));
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment);

        // act
        reconciler.reconcile(payment);

        // assert
        verify(paymentApplicationService).confirm(eq("TR:abc"), eq(PgTransactionStatus.SUCCESS), any());
    }

    @Test
    @DisplayName("거래키 보유 PENDING: PG 도 PENDING 이면 confirm 하지 않는다")
    void doesNotConfirmWhenStillPending() {
        // arrange
        PaymentModel payment = pending("TR:abc");
        when(pgClient.getTransaction("1", "TR:abc"))
            .thenReturn(Optional.of(new PgTransactionDetail("TR:abc", "000100", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, PgTransactionStatus.PENDING, null)));
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment);

        // act
        reconciler.reconcile(payment);

        // assert
        verify(paymentApplicationService, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("거래키 없는 PENDING(타임아웃): orderId 로 실제 거래를 찾아 키를 채우고 confirm 한다")
    void reconcileByOrderIdWhenNoKey() {
        // arrange
        PaymentModel payment = pending(null);
        when(pgClient.findTransactionsByOrderId("1", "000100"))
            .thenReturn(List.of(new PgTransactionDetail("TR:found", "000100", null, null, null, PgTransactionStatus.SUCCESS, null)));
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment);

        // act
        reconciler.reconcile(payment);

        // assert
        verify(paymentApplicationService).attachTransactionKey(PAYMENT_ID, "TR:found");
        verify(paymentApplicationService).confirm(eq("TR:found"), eq(PgTransactionStatus.SUCCESS), any());
    }

    @Test
    @DisplayName("거래키 없는 PENDING: PG 에 거래가 없으면 아무것도 하지 않는다")
    void noTransactionAtPg() {
        // arrange
        PaymentModel payment = pending(null);
        when(pgClient.findTransactionsByOrderId("1", "000100")).thenReturn(List.of());
        when(paymentApplicationService.getPayment(PAYMENT_ID)).thenReturn(payment);

        // act
        reconciler.reconcile(payment);

        // assert
        verify(paymentApplicationService, never()).attachTransactionKey(any(), any());
        verify(paymentApplicationService, never()).confirm(any(), any(), any());
    }
}
