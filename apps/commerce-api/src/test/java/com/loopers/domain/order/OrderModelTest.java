package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("주문 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보를 입력하면 PENDING 상태로 생성된다.")
        @Test
        void creates_with_pending_status() {
            OrderModel order = new OrderModel(1L, 30000L);

            assertAll(
                () -> assertThat(order.getMemberId()).isEqualTo(1L),
                () -> assertThat(order.getTotalAmount()).isEqualTo(30000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("회원 ID가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_member_id_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(null, 10000L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총금액이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_total_amount_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(1L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("총금액이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_total_amount_is_negative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(1L, -1L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태의 주문은 CONFIRMED로 전환된다.")
        @Test
        void confirms_pending_order() {
            OrderModel order = new OrderModel(1L, 10000L);

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("CANCELLED 상태의 주문을 확정하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_order_is_already_cancelled() {
            OrderModel order = new OrderModel(1L, 10000L);
            order.cancel();

            CoreException ex = assertThrows(CoreException.class, order::confirm);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("PENDING 상태의 주문은 CANCELLED로 전환된다.")
        @Test
        void cancels_pending_order() {
            OrderModel order = new OrderModel(1L, 10000L);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("CONFIRMED 상태의 주문을 취소하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_order_is_already_confirmed() {
            OrderModel order = new OrderModel(1L, 10000L);
            order.confirm();

            CoreException ex = assertThrows(CoreException.class, order::cancel);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 소유자를 확인할 때,")
    @Nested
    class IsOwnedBy {

        @DisplayName("본인의 주문이면 true를 반환한다.")
        @Test
        void returns_true_when_member_matches() {
            OrderModel order = new OrderModel(1L, 10000L);

            assertThat(order.isOwnedBy(1L)).isTrue();
        }

        @DisplayName("타인의 주문이면 false를 반환한다.")
        @Test
        void returns_false_when_member_does_not_match() {
            OrderModel order = new OrderModel(1L, 10000L);

            assertThat(order.isOwnedBy(99L)).isFalse();
        }
    }

    @DisplayName("주문 항목 모델을 생성할 때,")
    @Nested
    class OrderItemCreate {

        @DisplayName("올바른 정보를 입력하면 생성된다.")
        @Test
        void creates_with_valid_inputs() {
            OrderItemModel item = new OrderItemModel(1L, 2L, "상품명", 5000L, 3);

            assertAll(
                () -> assertThat(item.getOrderId()).isEqualTo(1L),
                () -> assertThat(item.getProductId()).isEqualTo(2L),
                () -> assertThat(item.getProductName()).isEqualTo("상품명"),
                () -> assertThat(item.getPrice()).isEqualTo(5000L),
                () -> assertThat(item.getQuantity()).isEqualTo(3),
                () -> assertThat(item.getSubtotal()).isEqualTo(15000L)
            );
        }

        @DisplayName("주문 ID가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_order_id_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(null, 1L, "상품명", 5000L, 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_product_name_is_blank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "  ", 5000L, 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("단가가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_price_is_negative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "상품명", -1L, 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_quantity_is_less_than_one() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "상품명", 5000L, 0));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("소계는 단가 × 수량으로 계산된다.")
        @Test
        void calculates_subtotal_correctly() {
            OrderItemModel item = new OrderItemModel(1L, 1L, "상품명", 3000L, 4);

            assertThat(item.getSubtotal()).isEqualTo(12000L);
        }
    }
}