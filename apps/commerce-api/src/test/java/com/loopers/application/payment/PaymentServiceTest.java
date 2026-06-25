package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 1001L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentService paymentService = new PaymentService(paymentRepository, orderRepository);

    private Order payableOrder(long finalAmount) {
        Order order = mock(Order.class);
        when(order.isOwnedBy(USER_ID)).thenReturn(true);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getFinalAmount()).thenReturn(finalAmount);
        return order;
    }

    @DisplayName("결제를 접수(createPending)할 때, ")
    @Nested
    class CreatePending {

        @DisplayName("결제 가능한 주문이고 활성 결제가 없으면, 주문 금액으로 PENDING 결제를 만들어 먼저 저장한다.")
        @Test
        void createsAndSavesPending() {
            // arrange
            Order order = payableOrder(5000L);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findActiveByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            Payment payment = paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG);

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payment.getAmount()).isEqualTo(5000L)
            );
            verify(paymentRepository).save(any(Payment.class)); // record-first
        }

        @DisplayName("이미 활성 결제가 있으면, 새로 만들지 않고 기존 결제를 반환한다. (멱등)")
        @Test
        void returnsExisting_whenActivePaymentExists() {
            // arrange
            Payment existing = Payment.pending(USER_ID, ORDER_ID, com.loopers.domain.vo.Money.of(5000L), CardType.SAMSUNG);
            Order order = payableOrder(5000L);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findActiveByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            // act
            Payment payment = paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG);

            // assert
            assertThat(payment).isSameAs(existing);
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("주문이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderMissing() {
            // arrange
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            // arrange
            Order order = mock(Order.class);
            when(order.isOwnedBy(USER_ID)).thenReturn(false);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 결제 완료(PAID)된 주문이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderNotPayable() {
            // arrange
            Order order = mock(Order.class);
            when(order.isOwnedBy(USER_ID)).thenReturn(true);
            when(order.getStatus()).thenReturn(OrderStatus.PAID);
            when(orderRepository.find(ORDER_ID)).thenReturn(Optional.of(order));

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.createPending(USER_ID, ORDER_ID, CardType.SAMSUNG));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 거래키를 반영(attachTransactionKey)할 때, ")
    @Nested
    class AttachTransactionKey {

        @DisplayName("PENDING 결제에 거래키를 기록하고 저장한다.")
        @Test
        void attachesKey() {
            // arrange
            Payment payment = Payment.pending(USER_ID, ORDER_ID, com.loopers.domain.vo.Money.of(5000L), CardType.SAMSUNG);
            when(paymentRepository.find(99L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            Payment result = paymentService.attachTransactionKey(99L, "20260625:TR:abc123");

            // assert
            assertThat(result.getTransactionKey()).isEqualTo("20260625:TR:abc123");
            verify(paymentRepository).save(payment);
        }

        @DisplayName("결제가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenPaymentMissing() {
            // arrange
            when(paymentRepository.find(99L)).thenReturn(Optional.empty());

            // act + assert
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentService.attachTransactionKey(99L, "k"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
