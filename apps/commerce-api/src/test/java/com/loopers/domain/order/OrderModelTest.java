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

    private static OrderItemModel item(long productId, long price, int quantity) {
        return new OrderItemModel(productId, "상품" + productId, "브랜드A", price, null, quantity);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("아이템들의 가격 × 수량 합계가 totalPrice 로 계산되고, status 는 PENDING 이다.")
        @Test
        void computesTotalAndPending() {
            // arrange
            List<OrderItemModel> items = List.of(item(1L, 1000L, 2), item(2L, 500L, 3));

            // act
            OrderModel order = new OrderModel(100L, items);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(100L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getTotalPrice()).isEqualTo(1000L * 2 + 500L * 3),
                () -> assertThat(order.totalAmount()).isEqualTo(3500L),
                () -> assertThat(order.getItems()).hasSize(2)
            );
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(100L, List.of())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderModel(null, List.of(item(1L, 1000L, 1)))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("반환된 items 는 불변 리스트라 외부에서 수정 불가능하다.")
        @Test
        void itemsAreImmutable() {
            // arrange
            OrderModel order = new OrderModel(100L, List.of(item(1L, 1000L, 1)));

            // act & assert
            assertThrows(UnsupportedOperationException.class, () ->
                order.getItems().add(item(2L, 500L, 1))
            );
        }
    }

    @DisplayName("OrderItem 의 subtotal 은 priceSnapshot × quantity 이다.")
    @Test
    void itemSubtotal() {
        // act
        long subtotal = item(1L, 1500L, 4).subtotal();

        // assert
        assertThat(subtotal).isEqualTo(6000L);
    }

    @DisplayName("주문 상태를 전이할 때, ")
    @Nested
    class Transition {
        @DisplayName("PENDING → PAID 전이는 정상 동작한다.")
        @Test
        void pendingToPaid() {
            // arrange
            OrderModel order = new OrderModel(100L, List.of(item(1L, 1000L, 1)));

            // act
            order.markPaid();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("PENDING → FAILED 전이는 정상 동작한다.")
        @Test
        void pendingToFailed() {
            // arrange
            OrderModel order = new OrderModel(100L, List.of(item(1L, 1000L, 1)));

            // act
            order.markFailed();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @DisplayName("이미 종료 상태(PAID)에서 markFailed 호출하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenTransitionFromTerminal() {
            // arrange
            OrderModel order = new OrderModel(100L, List.of(item(1L, 1000L, 1)));
            order.markPaid();

            // act
            CoreException result = assertThrows(CoreException.class, order::markFailed);

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("CANCELLED 상태에서 markPaid 호출하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_cancelledThenPaid() {
            // arrange
            OrderModel order = new OrderModel(100L, List.of(item(1L, 1000L, 1)));
            order.markCancelled();

            // act
            CoreException result = assertThrows(CoreException.class, order::markPaid);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
