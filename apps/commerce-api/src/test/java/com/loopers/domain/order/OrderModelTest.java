package com.loopers.domain.order;

import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
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

    private OrderItemModel item(Long productId, long price, int qty) {
        return new OrderItemModel(productId, "상품" + productId, Money.of(price), Quantity.of(qty));
    }

    @DisplayName("Order 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 항목으로 생성하면, 상태는 PENDING 이고 총액이 계산된다.")
        @Test
        void createsOrder_whenValid() {
            // act
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 2)));

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getTotalAmount()).isEqualTo(2000L),
                () -> assertThat(order.getItems()).hasSize(1)
            );
        }

        @DisplayName("여러 항목의 소계 합이 총액이 된다.")
        @Test
        void totalAmount_isSumOfSubtotals() {
            // act
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 2), item(20L, 500L, 3)));

            // assert
            assertThat(order.getTotalAmount()).isEqualTo(3500L); // 2000 + 1500
        }

        @DisplayName("유저 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(null, List.of(item(10L, 1000L, 1))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(USER_ID, null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(USER_ID, List.of()));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("금액 스냅샷을 기록할 때, ")
    @Nested
    class Amounts {

        @DisplayName("할인 금액을 받으면 총액/할인/최종 금액 3종이 기록된다.")
        @Test
        void recordsAmounts_withDiscount() {
            // act
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 2)), Money.of(500L), 42L);

            // assert
            assertAll(
                () -> assertThat(order.getTotalAmount()).isEqualTo(2000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(500L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(1500L),
                () -> assertThat(order.getUserCouponId()).isEqualTo(42L)
            );
        }

        @DisplayName("할인이 총액을 초과하면, 할인은 총액을 상한으로 하고 최종 금액은 0 원이다.")
        @Test
        void capsDiscountAtTotal() {
            // act
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 1)), Money.of(5000L), 42L);

            // assert
            assertAll(
                () -> assertThat(order.getDiscountAmount()).isEqualTo(1000L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(0L)
            );
        }

        @DisplayName("쿠폰 없이 생성하면, 할인 0 원에 최종 금액은 총액과 같다.")
        @Test
        void noCoupon_meansZeroDiscount() {
            // act
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 2)));

            // assert
            assertAll(
                () -> assertThat(order.getDiscountAmount()).isEqualTo(0L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(2000L),
                () -> assertThat(order.getUserCouponId()).isNull()
            );
        }
    }

    @DisplayName("주문 소유자를 확인할 때, ")
    @Nested
    class IsOwnedBy {

        @DisplayName("같은 유저면 true, 다른 유저나 null 이면 false 를 반환한다.")
        @Test
        void checksOwnership() {
            // arrange
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 1)));

            // act + assert
            assertAll(
                () -> assertThat(order.isOwnedBy(USER_ID)).isTrue(),
                () -> assertThat(order.isOwnedBy(999L)).isFalse(),
                () -> assertThat(order.isOwnedBy(null)).isFalse()
            );
        }
    }

    @DisplayName("주문 항목 목록은 불변뷰라, ")
    @Nested
    class Items {

        @DisplayName("외부에서 수정하려 하면 예외가 발생한다.")
        @Test
        void isUnmodifiable() {
            // arrange
            OrderModel order = new OrderModel(USER_ID, List.of(item(10L, 1000L, 1)));

            // act + assert
            assertThrows(UnsupportedOperationException.class, () -> order.getItems().clear());
        }
    }
}
