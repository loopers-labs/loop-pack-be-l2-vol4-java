package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderItem;
import com.loopers.domain.order.model.OrderItemStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 입력이면, ORDERED 상태로 생성된다.")
        @Test
        void createsOrderItem_withOrderedStatus() {
            // Arrange & Act
            OrderItem item = OrderItem.create(1L, 2L, 3);

            // Assert
            assertThat(item.getOrderId()).isEqualTo(1L);
            assertThat(item.getProductId()).isEqualTo(2L);
            assertThat(item.getQuantity()).isEqualTo(3);
            assertThat(item.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        }

        @DisplayName("quantity가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> OrderItem.create(1L, 2L, 0));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> OrderItem.create(1L, 2L, -1));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> OrderItem.create(null, 2L, 1));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> OrderItem.create(1L, null, 1));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 항목을 취소할 때, ")
    @Nested
    class Cancel {

        @DisplayName("ORDERED 상태이면, CANCELLED로 변경된다.")
        @Test
        void cancelsOrderItem_whenStatusIsOrdered() {
            // Arrange
            OrderItem item = OrderItem.create(1L, 2L, 3);

            // Act
            item.cancel();

            // Assert
            assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        }
    }
}
