package com.loopers.domain.order;

import com.loopers.domain.order.model.Order;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 입력이면, 주문이 생성된다.")
        @Test
        void createsOrder_whenInputIsValid() {
            // Arrange & Act
            Order order = Order.create(1L, 100_000L);

            // Assert
            assertThat(order.getMemberId()).isEqualTo(1L);
            assertThat(order.getTotalAmount()).isEqualTo(100_000L);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(null, 100_000L));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalAmount가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalAmountIsZero() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(1L, 0L));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalAmount가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalAmountIsNegative() {
            // Act
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(1L, -1L));

            // Assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
