package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 확정 단위({@link PaymentConfirmer#confirm}) 검증 — 콜백(§3.4)과 Reconcile(§3.5)이 공유한다.
 * 비관락 조회 → 결제 상태 전이 → 주문 cascade를 한 흐름으로 처리하며, 멱등(중복 확정/이미 확정된 주문)을 보장한다.
 */
class PaymentConfirmerTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final PaymentConfirmer confirmer = new PaymentConfirmer(paymentRepository, orderService);

    private static final String TX_KEY = "20260623:TR:abc123";
    private static final String RAW_CARD = "1234-5678-9814-1451";

    private PaymentModel pendingPaymentFor(long orderId) {
        PaymentModel payment = new PaymentModel(orderId, 1L, CardType.SAMSUNG, RAW_CARD, 5000L); // PENDING
        payment.assignTransactionKey(TX_KEY);
        return payment;
    }

    @Test
    @DisplayName("SUCCESS: 결제를 SUCCESS로 확정하고 주문을 markPaid 한다")
    void given_success_when_confirm_then_marksPaid() {
        PaymentModel payment = pendingPaymentFor(10L);
        when(paymentRepository.findByTransactionKeyForUpdate(TX_KEY)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentModel result = confirmer.confirm(TX_KEY, PaymentStatus.SUCCESS, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService).markPaid(10L);
        verify(orderService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("FAILED: 결제를 FAILED로 확정하고 주문을 markFailed(원복) 한다")
    void given_failed_when_confirm_then_marksFailed() {
        PaymentModel payment = pendingPaymentFor(10L);
        when(paymentRepository.findByTransactionKeyForUpdate(TX_KEY)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentModel result = confirmer.confirm(TX_KEY, PaymentStatus.FAILED, "한도 초과");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getReason()).isEqualTo("한도 초과");
        verify(orderService).markFailed(10L, "한도 초과");
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("이미 확정(SUCCESS)된 결제: 멱등 — 재반영 없이 현재 상태 반환")
    void given_alreadyConfirmed_when_confirm_then_idempotentSkip() {
        PaymentModel payment = pendingPaymentFor(10L);
        payment.markSuccess(); // 이미 확정
        when(paymentRepository.findByTransactionKeyForUpdate(TX_KEY)).thenReturn(Optional.of(payment));

        PaymentModel result = confirmer.confirm(TX_KEY, PaymentStatus.SUCCESS, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService, never()).markPaid(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 확정된 주문이면 markPaid CONFLICT를 멱등 skip 한다(예외 전파 없음)")
    void given_orderAlreadyConfirmed_when_success_then_skipsConflict() {
        PaymentModel payment = pendingPaymentFor(10L);
        when(paymentRepository.findByTransactionKeyForUpdate(TX_KEY)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderService.markPaid(10L)).thenThrow(new CoreException(ErrorType.CONFLICT, "이미 확정"));

        PaymentModel result = confirmer.confirm(TX_KEY, PaymentStatus.SUCCESS, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("알 수 없는 transactionKey는 NOT_FOUND")
    void given_unknownKey_when_confirm_then_notFound() {
        when(paymentRepository.findByTransactionKeyForUpdate("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirmer.confirm("nope", PaymentStatus.SUCCESS, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING 결과로는 결제를 확정하지 않는다(그대로 PENDING 유지)")
    void given_pendingResult_when_confirm_then_noTransition() {
        PaymentModel payment = pendingPaymentFor(10L);
        when(paymentRepository.findByTransactionKeyForUpdate(TX_KEY)).thenReturn(Optional.of(payment));

        PaymentModel result = confirmer.confirm(TX_KEY, PaymentStatus.PENDING, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(orderService, never()).markPaid(any());
        verify(orderService, never()).markFailed(any(), any());
    }
}
