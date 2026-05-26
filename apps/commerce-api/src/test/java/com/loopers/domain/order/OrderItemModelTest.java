package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    @DisplayName("주문 항목을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보로 생성하면 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenAllFieldsAreValid() {
            // arrange
            OrderModel order = new OrderModel(1L);

            // act
            OrderItemModel item = new OrderItemModel(order, 100L, "신발", 50_000L, 2);

            // assert
            assertAll(
                () -> assertThat(item.getProductId()).isEqualTo(100L),
                () -> assertThat(item.getProductName()).isEqualTo("신발"),
                () -> assertThat(item.getProductPrice()).isEqualTo(50_000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("수량이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0})
        void throwsBadRequest_whenQuantityIsZeroOrLess(int quantity) {
            OrderModel order = new OrderModel(1L);
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(order, 100L, "신발", 50_000L, quantity));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면 Quantity VO 에서 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            OrderModel order = new OrderModel(1L);
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(order, 100L, "신발", 50_000L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 가격이 음수이면 Money VO 에서 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductPriceIsNegative() {
            OrderModel order = new OrderModel(1L);
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(order, 100L, "신발", -1L, 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소계를 계산할 때,")
    @Nested
    class Subtotal {

        @DisplayName("상품 가격 × 수량 결과를 반환한다.")
        @Test
        void calculatesSubtotal() {
            OrderModel order = new OrderModel(1L);
            OrderItemModel item = new OrderItemModel(order, 100L, "신발", 50_000L, 3);

            assertThat(item.calculateSubtotal()).isEqualTo(150_000L);
        }
    }
}
