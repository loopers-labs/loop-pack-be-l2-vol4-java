package com.loopers.domain.order;

import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("쿠폰 미적용 시, 원금/할인/최종 금액이 일치하고 userCouponId 는 null 이다.")
        @Test
        void calculatesAmounts_whenNoCoupon() {
            // arrange: 1500 * 2 + 1000 * 1 = 4000
            List<OrderItem> items = List.of(
                OrderItem.of(1L, "상품A", Money.of(1_500L), Quantity.of(2)),
                OrderItem.of(2L, "상품B", Money.of(1_000L), Quantity.of(1))
            );

            // act
            Order order = Order.create(1L, items, null, null);

            // assert
            assertThat(order.getOriginalAmount()).isEqualTo(Money.of(4_000L));
            assertThat(order.getDiscountAmount()).isEqualTo(Money.zero());
            assertThat(order.getFinalAmount()).isEqualTo(Money.of(4_000L));
            assertThat(order.getUserCouponId()).isNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getItems()).hasSize(2);
        }

        @DisplayName("쿠폰 적용 시, 최종 금액은 원금에서 할인을 뺀 값이 되고 userCouponId 가 기록된다.")
        @Test
        void calculatesFinalAmount_whenCouponApplied() {
            // arrange: 원금 4000, 할인 1500
            List<OrderItem> items = List.of(
                OrderItem.of(1L, "상품A", Money.of(1_500L), Quantity.of(2)),
                OrderItem.of(2L, "상품B", Money.of(1_000L), Quantity.of(1))
            );

            // act
            Order order = Order.create(1L, items, 99L, Money.of(1_500L));

            // assert
            assertThat(order.getOriginalAmount()).isEqualTo(Money.of(4_000L));
            assertThat(order.getDiscountAmount()).isEqualTo(Money.of(1_500L));
            assertThat(order.getFinalAmount()).isEqualTo(Money.of(2_500L));
            assertThat(order.getUserCouponId()).isEqualTo(99L);
        }

        @DisplayName("할인 금액이 원금보다 크면, Money 의 음수 금지 정책에 따라 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountExceedsOriginal() {
            List<OrderItem> items = List.of(OrderItem.of(1L, "상품A", Money.of(1_000L), Quantity.of(1)));

            CoreException result = assertThrows(CoreException.class,
                () -> Order.create(1L, items, 1L, Money.of(2_000L)));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> Order.create(1L, List.of(), null, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유저 정보가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIsNull() {
            List<OrderItem> items = List.of(OrderItem.of(1L, "상품A", Money.of(1_000L), Quantity.of(1)));

            CoreException result = assertThrows(CoreException.class,
                () -> Order.create(null, items, null, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
