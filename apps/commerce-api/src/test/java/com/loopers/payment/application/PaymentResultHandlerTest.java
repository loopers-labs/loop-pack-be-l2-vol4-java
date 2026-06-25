package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.order.application.OrderPaymentService;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentErrorCode;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentResultHandlerTest {

    private static final String TRANSACTION_KEY = "20260625:TR:7466dd";
    private static final String ORDER_NUMBER = "20260625-000032";
    private static final long AMOUNT = 58_000L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderPaymentService orderPaymentService = mock(OrderPaymentService.class);
    private final PaymentResultHandler handler = new PaymentResultHandler(paymentRepository, orderPaymentService);

    private Payment pendingPaymentWithKey() {
        Payment payment = Payment.create(ORDER_NUMBER, Money.of(AMOUNT));
        payment.assignTransaction(TRANSACTION_KEY, PgProvider.TOSS);
        return payment;
    }

    private PaymentCommand.Confirm confirm(PaymentStatus status, String reason) {
        return new PaymentCommand.Confirm(TRANSACTION_KEY, ORDER_NUMBER, AMOUNT, status, reason);
    }

    @Test
    @DisplayName("SUCCESS 통보면 결제를 SUCCESS 로 확정하고 주문을 markPaid 한다")
    void givenPendingPayment_whenSuccess_thenMarksSuccessAndOrderPaid() {
        Payment payment = pendingPaymentWithKey();
        when(paymentRepository.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));

        handler.handle(confirm(PaymentStatus.SUCCESS, null));

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> verify(orderPaymentService).markPaid(ORDER_NUMBER)
        );
    }

    @Test
    @DisplayName("FAILED 통보면 결제를 FAILED 로 확정하고 주문을 보상한다")
    void givenPendingPayment_whenFailed_thenMarksFailedAndCompensates() {
        Payment payment = pendingPaymentWithKey();
        when(paymentRepository.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));

        handler.handle(confirm(PaymentStatus.FAILED, "한도초과"));

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도초과"),
                () -> verify(orderPaymentService).compensate(ORDER_NUMBER)
        );
    }

    @Test
    @DisplayName("transactionKey 로 결제를 못 찾으면 무시한다(대사가 회수)")
    void givenUnknownTransactionKey_whenHandle_thenIgnored() {
        when(paymentRepository.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.empty());

        handler.handle(confirm(PaymentStatus.SUCCESS, null));

        verifyNoInteractions(orderPaymentService);
    }

    @Test
    @DisplayName("이미 terminal 인 결제는 다시 전이하지 않는다(멱등)")
    void givenAlreadyTerminalPayment_whenHandle_thenIgnored() {
        Payment payment = pendingPaymentWithKey();
        payment.markSuccess();
        when(paymentRepository.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));

        handler.handle(confirm(PaymentStatus.FAILED, "한도초과"));

        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> verifyNoInteractions(orderPaymentService)
        );
    }

    @Test
    @DisplayName("콜백의 orderNumber·amount 가 결제와 불일치하면 PAYMENT_CALLBACK_INVALID 가 발생한다")
    void givenMismatchedCallback_whenHandle_thenThrowsCallbackInvalid() {
        Payment payment = pendingPaymentWithKey();
        when(paymentRepository.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));

        PaymentCommand.Confirm tampered =
                new PaymentCommand.Confirm(TRANSACTION_KEY, ORDER_NUMBER, AMOUNT + 1, PaymentStatus.SUCCESS, null);

        assertThatThrownBy(() -> handler.handle(tampered))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_CALLBACK_INVALID);
    }
}
