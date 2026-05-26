package com.loopers.domain.order;

import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("총 금액은 모든 주문 항목 소계의 합으로 계산된다.")
        @Test
        void calculatesTotalPrice() {
            // arrange: 1500 * 2 + 1000 * 1 = 4000
            List<OrderItem> items = List.of(
                OrderItem.of(1L, "상품A", Money.of(1_500L), Quantity.of(2)),
                OrderItem.of(2L, "상품B", Money.of(1_000L), Quantity.of(1))
            );

            // act
            Order order = Order.create(1L, items);

            // assert
            assertThat(order.getTotalPrice()).isEqualTo(Money.of(4_000L));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getItems()).hasSize(2);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            CoreException result = assertThrows(CoreException.class, () -> Order.create(1L, List.of()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유저 정보가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIsNull() {
            List<OrderItem> items = List.of(OrderItem.of(1L, "상품A", Money.of(1_000L), Quantity.of(1)));

            CoreException result = assertThrows(CoreException.class, () -> Order.create(null, items));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
