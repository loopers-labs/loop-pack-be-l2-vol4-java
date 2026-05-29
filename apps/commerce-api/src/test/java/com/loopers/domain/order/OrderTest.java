package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final ProductSnapshot SNAPSHOT =
        new ProductSnapshot("나이키 신발", 50000L, "나이키");

    private List<OrderItem> singleItem() {
        return List.of(new OrderItem(1L, 2, SNAPSHOT));
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 항목이 있으면 PAID 상태로 생성된다.")
        @Test
        void creates_withPaidStatus() {
            Order order = new Order(1L, singleItem());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getItems()).hasSize(1);
        }

        @DisplayName("항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(1L, List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("항목이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(1L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("PAID 상태이면 CANCELLED로 전이된다.")
        @Test
        void cancels_whenPaid() {
            Order order = new Order(1L, singleItem());
            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 취소된 주문을 다시 취소하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCancelled() {
            Order order = new Order(1L, singleItem());
            order.cancel();

            CoreException result = assertThrows(CoreException.class, order::cancel);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class BelongsTo {

        @DisplayName("본인 주문이면 true를 반환한다.")
        @Test
        void returnsTrue_whenOwner() {
            Order order = new Order(1L, singleItem());
            assertThat(order.belongsTo(1L)).isTrue();
        }

        @DisplayName("타인 주문이면 false를 반환한다.")
        @Test
        void returnsFalse_whenNotOwner() {
            Order order = new Order(1L, singleItem());
            assertThat(order.belongsTo(2L)).isFalse();
        }
    }
}
