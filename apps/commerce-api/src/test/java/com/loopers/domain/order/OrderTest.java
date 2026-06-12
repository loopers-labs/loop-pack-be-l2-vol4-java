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

class OrderTest {

    @DisplayName("주문 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("주문 가능한 상품 라인이 있으면, 주문을 생성한다.")
        @Test
        void createsOrderModel_whenOrderLinesAreValid() {
            // arrange
            OrderLine orderLine = new OrderLine(1L, "니트", 30_000L, 2);

            // act
            Order order = new Order("user1234", List.of(orderLine));

            // assert
            assertAll(
                () -> assertThat(order.getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getOrderLines()).containsExactly(orderLine),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(60_000L),
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getFinalAmount()).isEqualTo(60_000L),
                () -> assertThat(order.getTotalAmount()).isEqualTo(60_000L)
            );
        }

        @DisplayName("회원 로그인 ID가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Order(" ", List.of(new OrderLine(1L, "니트", 30_000L, 2)));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 상품 라인이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenOrderLinesAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Order("user1234", List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인을 적용할 때, ")
    @Nested
    class ApplyDiscount {
        @DisplayName("할인 금액만큼 최종 금액을 차감한다.")
        @Test
        void appliesDiscountAmount() {
            // arrange
            Order order = new Order("user1234", List.of(new OrderLine(1L, "니트", 30_000L, 2)));

            // act
            order.applyDiscount(5_000L);

            // assert
            assertAll(
                () -> assertThat(order.getOriginalAmount()).isEqualTo(60_000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(5_000L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(55_000L),
                () -> assertThat(order.getTotalAmount()).isEqualTo(55_000L)
            );
        }

        @DisplayName("할인 금액이 주문 금액보다 크면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDiscountAmountIsGreaterThanOriginalAmount() {
            // arrange
            Order order = new Order("user1234", List.of(new OrderLine(1L, "니트", 30_000L, 2)));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                order.applyDiscount(60_001L);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(order.getDiscountAmount()).isZero()
            );
        }
    }
}
