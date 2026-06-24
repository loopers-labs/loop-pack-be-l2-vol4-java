package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.order.application.OrderInfo;
import com.loopers.order.application.OrderReader;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentErrorCode;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final Long ORDER_ID = 100L;
    private static final long FINAL_AMOUNT = 55_000L;
    private static final Long PAYMENT_ID = 1L;
    private static final String TRANSACTION_KEY = "tx-0001";

    private final OrderReader orderReader = mock(OrderReader.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentService paymentService = new PaymentService(orderReader, paymentRepository);

    private OrderInfo payableOrder() {
        return new OrderInfo(ORDER_ID, true, FINAL_AMOUNT);
    }

    @Test
    @DisplayName("결제 가능한 주문이면 주문 최종금액으로 PENDING 결제를 생성해 저장한다")
    void givenPayableOrder_whenCreatePending_thenSavesPendingPaymentWithFinalAmount() {
        when(orderReader.findForPayment(ORDER_ID)).thenReturn(Optional.of(payableOrder()));
        when(paymentRepository.findActiveByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPending(ORDER_ID);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(saved.getAmount()).isEqualTo(Money.of(FINAL_AMOUNT)),
                () -> assertThat(saved.getTransactionKey()).isNull(),
                () -> assertThat(saved.getPgProvider()).isNull()
        );
    }

    @Test
    @DisplayName("주문이 없으면 PAYMENT_ORDER_NOT_FOUND 가 발생한다")
    void givenMissingOrder_whenCreatePending_thenThrowsOrderNotFound() {
        when(orderReader.findForPayment(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPending(ORDER_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문이 결제 가능한 상태가 아니면 PAYMENT_ORDER_NOT_PAYABLE 가 발생한다")
    void givenNotPayableOrder_whenCreatePending_thenThrowsNotPayable() {
        when(orderReader.findForPayment(ORDER_ID)).thenReturn(Optional.of(new OrderInfo(ORDER_ID, false, FINAL_AMOUNT)));

        assertThatThrownBy(() -> paymentService.createPending(ORDER_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    }

    @Test
    @DisplayName("해당 주문에 활성 결제가 이미 있으면 PAYMENT_ALREADY_IN_PROGRESS 가 발생한다")
    void givenActivePaymentExists_whenCreatePending_thenThrowsAlreadyInProgress() {
        when(orderReader.findForPayment(ORDER_ID)).thenReturn(Optional.of(payableOrder()));
        when(paymentRepository.findActiveByOrderId(ORDER_ID))
                .thenReturn(Optional.of(Payment.create(ORDER_ID, Money.of(FINAL_AMOUNT))));

        assertThatThrownBy(() -> paymentService.createPending(ORDER_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
    }

    @Test
    @DisplayName("assignTransaction 은 PENDING 결제에 거래키와 provider 를 채우고 상태는 PENDING 을 유지한다")
    void givenPendingPayment_whenAssignTransaction_thenFillsKeyAndProvider() {
        Payment pending = Payment.create(ORDER_ID, Money.of(FINAL_AMOUNT));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(pending));

        paymentService.assignTransaction(PAYMENT_ID, TRANSACTION_KEY, PgProvider.TOSS);

        assertAll(
                () -> assertThat(pending.getTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(pending.getPgProvider()).isEqualTo(PgProvider.TOSS),
                () -> assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING)
        );
    }

    @Test
    @DisplayName("거래키를 확정할 결제가 없으면 예외가 발생한다")
    void givenMissingPayment_whenAssignTransaction_thenThrows() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.assignTransaction(PAYMENT_ID, TRANSACTION_KEY, PgProvider.TOSS))
                .isInstanceOf(CoreException.class);
    }
}
