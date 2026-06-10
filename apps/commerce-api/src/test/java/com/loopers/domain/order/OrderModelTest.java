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

    private static final Long USER_ID = 1L;

    private OrderItem item(long productId, long unitPrice, int quantity) {
        return new OrderItem(productId, "상품-" + productId, unitPrice, quantity);
    }

    @DisplayName("주문 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 유저와 항목으로 생성하면 status=CREATED, totalAmount는 항목 subtotal 합계로 초기화된다")
        @Test
        void createsOrder_whenValid() {
            OrderItem a = item(101L, 10_000L, 2);   // 20_000
            OrderItem b = item(102L, 5_000L, 3);    // 15_000

            OrderModel order = new OrderModel(USER_ID, List.of(a, b));

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getTotalAmount().value()).isEqualTo(35_000L),
                () -> assertThat(order.getItems()).containsExactly(a, b)
            );
        }

        @DisplayName("생성 시 각 항목의 order 참조가 자동으로 부모와 연결된다 (양방향 동기화)")
        @Test
        void wiresBothEnds_onCreation() {
            OrderItem itm = item(101L, 1_000L, 1);

            OrderModel order = new OrderModel(USER_ID, List.of(itm));

            assertThat(itm.getOrder()).isSameAs(order);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(null, List.of(item(1L, 100L, 1))));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 null이거나 비어있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenItemsAreNullOrEmpty() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, null)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, List.of())).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("주문 항목 노출 시")
    @Nested
    class ItemExposure {

        @DisplayName("외부에 노출되는 items 리스트는 수정 불가능하다 (불변 컬렉션)")
        @Test
        void itemsListIsUnmodifiable() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)));

            assertThrows(UnsupportedOperationException.class,
                () -> order.getItems().add(item(2L, 200L, 1)));
        }
    }
}
