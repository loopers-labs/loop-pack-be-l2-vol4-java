package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentReconcilerTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 1001L; // PgDto.orderId → "001001"

    private final PgPaymentClient pgClient = mock(PgPaymentClient.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentReconciler reconciler = new PaymentReconciler(pgClient, paymentService);

    private Payment pendingPayment(String transactionKey) {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(payment.getUserId()).thenReturn(USER_ID);
        when(payment.getOrderId()).thenReturn(ORDER_ID);
        when(payment.getId()).thenReturn(50L);
        when(payment.getTransactionKey()).thenReturn(transactionKey);
        return payment;
    }

    private PgDto.Envelope<PgDto.TransactionDetailResponse> detail(String status, String reason) {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
            new PgDto.TransactionDetailResponse("TKEY", "001001", "SAMSUNG", "1234-5678-9814-1451", 5000L, status, reason));
    }

    private PgDto.Envelope<PgDto.OrderResponse> orderResp(String key, String status) {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
            new PgDto.OrderResponse("001001", List.of(new PgDto.TransactionResponse(key, status, null))));
    }

    private <T> PgDto.Envelope<T> unavailable() {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("FAIL", "PG_UNAVAILABLE", "미확정"), null);
    }

    @DisplayName("거래키가 있는 PENDING 결제를 reconcile 할 때, ")
    @Nested
    class WithKey {

        @DisplayName("PG가 SUCCESS면, 결과를 반영한다(applyResult).")
        @Test
        void appliesResult_whenTerminal() {
            Payment payment = pendingPayment("TKEY");
            when(pgClient.getTransaction("7", "TKEY")).thenReturn(detail("SUCCESS", null));

            reconciler.reconcile(payment);

            verify(paymentService).applyResult("TKEY", PaymentStatus.SUCCESS, null);
        }

        @DisplayName("PG가 아직 PENDING이면, 아무 것도 반영하지 않는다(다음 폴링에).")
        @Test
        void noApply_whenStillPending() {
            Payment payment = pendingPayment("TKEY");
            when(pgClient.getTransaction("7", "TKEY")).thenReturn(detail("PENDING", null));

            reconciler.reconcile(payment);

            verify(paymentService, never()).applyResult(any(), any(), any());
        }

        @DisplayName("PG 조회가 미확정(fallback)이면, 아무 것도 반영하지 않는다.")
        @Test
        void noApply_whenPgUnavailable() {
            Payment payment = pendingPayment("TKEY");
            when(pgClient.getTransaction("7", "TKEY")).thenReturn(unavailable());

            reconciler.reconcile(payment);

            verify(paymentService, never()).applyResult(any(), any(), any());
        }
    }

    @DisplayName("거래키가 없는(타임아웃) PENDING 결제를 reconcile 할 때, ")
    @Nested
    class WithoutKey {

        @DisplayName("orderId로 PG를 조회해 거래를 입양(키 부여)하고 결과를 반영한다.")
        @Test
        void adoptsAndApplies() {
            Payment payment = pendingPayment(null);
            when(pgClient.findByOrderId("7", "001001")).thenReturn(orderResp("ADOPTED-KEY", "SUCCESS"));

            reconciler.reconcile(payment);

            verify(paymentService).attachTransactionKey(50L, "ADOPTED-KEY");
            verify(paymentService).applyResult("ADOPTED-KEY", PaymentStatus.SUCCESS, null);
        }

        @DisplayName("PG에 거래가 없으면(미도달), 아무 것도 하지 않는다.")
        @Test
        void noOp_whenNoTransactionAtPg() {
            Payment payment = pendingPayment(null);
            when(pgClient.findByOrderId("7", "001001"))
                .thenReturn(new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
                    new PgDto.OrderResponse("001001", List.of())));

            reconciler.reconcile(payment);

            verify(paymentService, never()).attachTransactionKey(any(), any());
            verify(paymentService, never()).applyResult(any(), any(), any());
        }
    }

    @DisplayName("이미 터미널인 결제면, PG 조회 없이 아무 것도 하지 않는다.")
    @Test
    void noOp_whenNotPending() {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);

        reconciler.reconcile(payment);

        verifyNoInteractions(pgClient, paymentService);
    }
}
