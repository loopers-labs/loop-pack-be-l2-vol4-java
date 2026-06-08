package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("OrderModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("단일 라인이 주어지면, totalAmount = price × quantity 로 계산되고 상태는 CREATED 가 된다.")
        @Test
        void createsOrderModel_whenSingleLineIsGiven() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            OrderModel order = OrderModel.create(userId, lines);

            // then
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getTotalAmount()).isEqualTo(200_000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED)
            );
        }

        @DisplayName("여러 라인이 주어지면, totalAmount = Σ(price × quantity) 로 합산된다.")
        @Test
        void createsOrderModel_whenMultipleLinesAreGiven() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(200L, 3, "스탠스미스", 50_000L, "아디다스")
            ));

            // when
            OrderModel order = OrderModel.create(userId, lines);

            // then
            assertThat(order.getTotalAmount()).isEqualTo(100_000L + 50_000L * 3);
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // given
            Long userId = null;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(userId, lines));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("유저 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("lines 가 null 이면, EMPTY_ORDER_ITEMS 예외가 발생한다.")
        @Test
        void throwsEmptyOrderItemsException_whenLinesIsNull() {
            // given
            Long userId = 1L;
            OrderLines lines = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(userId, lines));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.EMPTY_ORDER_ITEMS);
        }

        @DisplayName("lines 가 빈 OrderLines 이면, EMPTY_ORDER_ITEMS 예외가 발생한다.")
        @Test
        void throwsEmptyOrderItemsException_whenLinesIsEmpty() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(userId, lines));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.EMPTY_ORDER_ITEMS);
        }

        @DisplayName("쿠폰 없이 생성하면, discountAmount = 0, finalAmount = totalAmount, usedCouponId = null 이다.")
        @Test
        void createsOrderWithoutDiscount_whenNoCouponIsApplied() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            OrderModel order = OrderModel.create(userId, lines);

            // then
            assertAll(
                () -> assertThat(order.getTotalAmount()).isEqualTo(200_000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(0L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(200_000L),
                () -> assertThat(order.getUsedCouponId()).isNull()
            );
        }

        @DisplayName("할인이 적용되면, finalAmount = totalAmount − discountAmount 로 계산되고 usedCouponId 가 스냅샷된다.")
        @Test
        void createsOrderWithDiscount_whenCouponIsApplied() {
            // given
            Long userId = 1L;
            Long usedCouponId = 77L;
            long discountAmount = 30_000L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            OrderModel order = OrderModel.create(userId, lines, discountAmount, usedCouponId);

            // then
            assertAll(
                () -> assertThat(order.getTotalAmount()).isEqualTo(200_000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(30_000L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(170_000L),
                () -> assertThat(order.getUsedCouponId()).isEqualTo(77L)
            );
        }

        @DisplayName("할인 금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDiscountAmountIsNegative() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(userId, lines, -1L, 77L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("할인 금액은 음수일 수 없습니다.")
            );
        }

        @DisplayName("할인 금액이 주문 금액을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDiscountAmountExceedsTotalAmount() {
            // given
            Long userId = 1L;
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(userId, lines, 100_001L, 77L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("할인 금액은 주문 금액을 초과할 수 없습니다.")
            );
        }
    }
}
