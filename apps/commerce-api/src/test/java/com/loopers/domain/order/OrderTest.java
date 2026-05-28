package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final Long USER_ID = 10L;

    private static OrderItem item(long productId, long unitPrice, int qty) {
        return OrderItem.of(productId, "상품 " + productId, Money.of(unitPrice), qty);
    }

    @DisplayName("Order 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 id 는 null, status 는 CREATED, totalAmount 는 항목 소계 합이다.")
        @Test
        void createsCreatedOrder_whenValid() {
            // arrange
            List<OrderItem> items = List.of(
                    item(1L, 1_000L, 2),  // 2_000
                    item(2L, 500L, 3)     // 1_500
            );

            // act
            Order order = Order.create(USER_ID, items);

            // assert
            assertThat(order.getId()).isNull();
            assertThat(order.getUserId()).isEqualTo(USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(3_500L);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getOrderedAt()).isNotNull();
        }

        @DisplayName("주문 시각 orderedAt 은 생성 시점으로 부여된다.")
        @Test
        void setsOrderedAt_atCreation() {
            // arrange
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            List<OrderItem> items = List.of(item(1L, 100L, 1));

            // act
            Order order = Order.create(USER_ID, items);

            // assert
            assertThat(order.getOrderedAt()).isAfter(before);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            List<OrderItem> items = List.of(item(1L, 100L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(null, items));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items 가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Order 를 restore 로 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("정상 값이면 모든 필드가 그대로 복원된다.")
        @Test
        void restoresOrder_whenValid() {
            // arrange
            List<OrderItem> items = List.of(item(1L, 1_000L, 2));
            LocalDateTime orderedAt = LocalDateTime.of(2026, 5, 28, 12, 0);

            // act
            Order order = Order.restore(
                    99L, USER_ID, items, Money.of(2_000L), OrderStatus.CREATED, orderedAt
            );

            // assert
            assertThat(order.getId()).isEqualTo(99L);
            assertThat(order.getUserId()).isEqualTo(USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(2_000L);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getOrderedAt()).isEqualTo(orderedAt);
        }

        @DisplayName("id 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenIdIsNull() {
            List<OrderItem> items = List.of(item(1L, 100L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.restore(null, USER_ID, items, Money.of(100L),
                            OrderStatus.CREATED, LocalDateTime.now()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Order 의 isOwnedBy 는 ")
    @Nested
    class IsOwnedBy {

        @DisplayName("동일한 userId 면 true 를 반환한다.")
        @Test
        void returnsTrue_whenSameUserId() {
            Order order = Order.create(USER_ID, List.of(item(1L, 100L, 1)));
            assertThat(order.isOwnedBy(USER_ID)).isTrue();
        }

        @DisplayName("다른 userId 면 false 를 반환한다.")
        @Test
        void returnsFalse_whenDifferentUserId() {
            Order order = Order.create(USER_ID, List.of(item(1L, 100L, 1)));
            assertThat(order.isOwnedBy(999L)).isFalse();
        }

        @DisplayName("null 이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenNull() {
            Order order = Order.create(USER_ID, List.of(item(1L, 100L, 1)));
            assertThat(order.isOwnedBy(null)).isFalse();
        }
    }

    @DisplayName("Order 의 items 는 외부 변경으로부터 보호된다 (방어적 복사).")
    @Test
    void itemsAreDefensivelyCopied() {
        // arrange
        OrderItem original = item(1L, 100L, 1);
        java.util.ArrayList<OrderItem> mutable = new java.util.ArrayList<>();
        mutable.add(original);
        Order order = Order.create(USER_ID, mutable);

        // act
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().add(item(2L, 200L, 1)));

        // assert
        assertThat(order.getItems()).hasSize(1);
    }
}
