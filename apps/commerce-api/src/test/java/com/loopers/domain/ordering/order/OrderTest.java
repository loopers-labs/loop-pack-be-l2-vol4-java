package com.loopers.domain.ordering.order;

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

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("주문 항목 스냅샷과 총액을 저장하고 결제 대기 상태가 된다.")
        @Test
        void createsPaymentPendingOrder_withSnapshotLines() {
            // arrange
            OrderLine line1 = new OrderLine(1L, "상품1", 1_000L, 2);
            OrderLine line2 = new OrderLine(2L, "상품2", 2_000L, 1);

            // act
            Order order = new Order("user1", List.of(line1, line2));

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo("user1"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING),
                () -> assertThat(order.getTotalAmount()).isEqualTo(4_000L),
                () -> assertThat(order.getLines()).hasSize(2),
                () -> assertThat(order.getLines().get(0).getProductName()).isEqualTo("상품1"),
                () -> assertThat(order.getLines().get(0).getLineAmount()).isEqualTo(2_000L)
            );
        }

        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLinesAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Order("user1", List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
