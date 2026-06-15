package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

        @DisplayName("할인액 0이면 id 는 미할당(0), status 는 CREATED, 적용 전·최종 금액은 항목 소계 합이다.")
        @Test
        void createsCreatedOrder_whenValid() {
            // arrange
            List<OrderItem> items = List.of(
                    item(1L, 1_000L, 2),  // 2_000
                    item(2L, 500L, 3)     // 1_500
            );

            // act
            Order order = Order.create(USER_ID, OrderItems.from(items), Money.of(0));

            // assert
            assertThat(order.getId()).isEqualTo(0L);
            assertThat(order.getUserId()).isEqualTo(USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getOriginalAmount().getAmount()).isEqualTo(3_500L);
            assertThat(order.getDiscountAmount().getAmount()).isEqualTo(0L);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(3_500L);
            assertThat(order.getItems()).hasSize(2);
            // orderedAt(=createdAt) 은 영속 시점에 부여되므로 영속 전(transient)에는 null 이다.
            assertThat(order.getOrderedAt()).isNull();
        }

        @DisplayName("할인액이 있으면 최종 금액은 적용 전 금액에서 할인액을 뺀 값이다. (AC-07-6)")
        @Test
        void appliesDiscount_toTotalAmount() {
            List<OrderItem> items = List.of(item(1L, 1_000L, 3)); // 3_000

            Order order = Order.create(USER_ID, OrderItems.from(items), Money.of(500));

            assertThat(order.getOriginalAmount().getAmount()).isEqualTo(3_000L);
            assertThat(order.getDiscountAmount().getAmount()).isEqualTo(500L);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(2_500L);
        }

        @DisplayName("할인액이 적용 전 금액과 같으면 최종 금액은 0원이다. (AC-07-9)")
        @Test
        void allowsTotalZero_whenDiscountEqualsOriginal() {
            List<OrderItem> items = List.of(item(1L, 1_000L, 2)); // 2_000

            Order order = Order.create(USER_ID, OrderItems.from(items), Money.of(2_000));

            assertThat(order.getTotalAmount().getAmount()).isEqualTo(0L);
        }

        @DisplayName("할인액이 적용 전 금액을 초과하면 BAD_REQUEST 예외가 발생한다. (AC-07-9)")
        @Test
        void throwsBadRequest_whenDiscountExceedsOriginal() {
            List<OrderItem> items = List.of(item(1L, 1_000L, 1)); // 1_000
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, OrderItems.from(items), Money.of(1_001)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인액이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountIsNull() {
            List<OrderItem> items = List.of(item(1L, 100L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, OrderItems.from(items), null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            List<OrderItem> items = List.of(item(1L, 100L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(null, OrderItems.from(items), Money.of(0)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, null, Money.of(0)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items 가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Order.create(USER_ID, OrderItems.from(List.of()), Money.of(0)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Order 의 isOwnedBy 는 ")
    @Nested
    class IsOwnedBy {

        @DisplayName("동일한 userId 면 true 를 반환한다.")
        @Test
        void returnsTrue_whenSameUserId() {
            Order order = Order.create(USER_ID, OrderItems.from(List.of(item(1L, 100L, 1))), Money.of(0));
            assertThat(order.isOwnedBy(USER_ID)).isTrue();
        }

        @DisplayName("다른 userId 면 false 를 반환한다.")
        @Test
        void returnsFalse_whenDifferentUserId() {
            Order order = Order.create(USER_ID, OrderItems.from(List.of(item(1L, 100L, 1))), Money.of(0));
            assertThat(order.isOwnedBy(999L)).isFalse();
        }

        @DisplayName("null 이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenNull() {
            Order order = Order.create(USER_ID, OrderItems.from(List.of(item(1L, 100L, 1))), Money.of(0));
            assertThat(order.isOwnedBy(null)).isFalse();
        }
    }

    @DisplayName("Order 의 items 는 외부 변경으로부터 보호된다 (방어적 복사).")
    @Test
    void itemsAreDefensivelyCopied() {
        // arrange
        OrderItem original = item(1L, 100L, 1);
        List<OrderItem> mutable = new ArrayList<>();
        mutable.add(original);
        Order order = Order.create(USER_ID, OrderItems.from(mutable), Money.of(0));

        // act
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().add(item(2L, 200L, 1)));

        // assert
        assertThat(order.getItems()).hasSize(1);
    }
}
