package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class OrderModelTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("총액은 주문 항목 총액의 합이고, 상태는 PENDING이다.")
        @Test
        void aggregatesTotalPrice_andSetsPending() {
            // arrange
            List<OrderLine> lines = List.of(
                OrderLine.create(1L, "상품A", 1_000L, 2),
                OrderLine.create(2L, "상품B", 3_000L, 1)
            );

            // act
            OrderModel order = OrderModel.create(10L, lines);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(10L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(5_000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getOrderLines()).hasSize(2)
            );
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderLinesAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(10L, List.of()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            List<OrderLine> lines = List.of(OrderLine.create(1L, "상품A", 1_000L, 1));

            CoreException result = assertThrows(CoreException.class,
                () -> new OrderModel(1L, null, lines, 1_000L, OrderStatus.PENDING, null, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
