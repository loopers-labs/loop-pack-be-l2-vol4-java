package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("Order 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId, items가 모두 유효하면, status는 PENDING_PAYMENT로 초기화된다.")
        @Test
        void createsOrderModel_withPendingPaymentStatus_whenAllFieldsAreValid() {
            // arrange
            Long userId = 1L;
            List<OrderItemModel> items = List.of(
                new OrderItemModel(1L, "에어맥스", 150000L, 2)
            );

            // act
            OrderModel order = new OrderModel(userId, items);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // arrange
            List<OrderItemModel> items = List.of(new OrderItemModel(1L, "에어맥스", 150000L, 2));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(null, items)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(1L, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(1L, Collections.emptyList())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 확정(confirm)할 때,")
    @Nested
    class Confirm {

        @DisplayName("confirm()을 호출하면, status가 CONFIRMED로 변경된다.")
        @Test
        void changesStatusToConfirmed_whenConfirmIsCalled() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));

            // act
            order.confirm();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @DisplayName("결제를 시작(startPayment)할 때,")
    @Nested
    class StartPayment {

        @DisplayName("PENDING_PAYMENT 상태이면, status가 IN_PAYMENT로 변경된다.")
        @Test
        void changesStatusToInPayment_whenStatusIsPendingPayment() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));

            // act
            order.startPayment();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT);
        }

        @DisplayName("IN_PAYMENT 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsInPayment() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            order.startPayment();

            // act
            CoreException result = assertThrows(CoreException.class, order::startPayment);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("CONFIRMED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsConfirmed() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            order.startPayment();
            order.confirm();

            // act
            CoreException result = assertThrows(CoreException.class, order::startPayment);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("PAYMENT_FAILED 상태이면, status가 IN_PAYMENT로 변경된다.")
        @Test
        void changesStatusToInPayment_whenStatusIsPaymentFailed() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            order.startPayment();
            order.failPayment();

            // act
            order.startPayment();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT);
        }
    }

}
