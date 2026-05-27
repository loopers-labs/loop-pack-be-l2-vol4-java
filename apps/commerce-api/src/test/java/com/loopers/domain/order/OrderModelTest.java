package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("OrderModel 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 userId로 생성 시 PENDING 상태로 초기화된다.")
        @Test
        void initializesWithPendingStatus_whenValidUserIdProvided() {
            // act
            OrderModel order = new OrderModel(1L);

            // assert
            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getTotalAmount()).isEqualTo(0);
            assertThat(order.getItems()).isEmpty();
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new OrderModel(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
