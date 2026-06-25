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

        @DisplayName("쿠폰 없이 정상 입력이면, 주문이 생성되고 totalAmount = originalAmount다.")
        @Test
        void createsOrder_withoutCoupon() {
            Order order = Order.create(1L, 100_000L, 0L, null);

            assertThat(order.getMemberId()).isEqualTo(1L);
            assertThat(order.getOriginalAmount()).isEqualTo(100_000L);
            assertThat(order.getDiscountAmount()).isEqualTo(0L);
            assertThat(order.getTotalAmount()).isEqualTo(100_000L);
            assertThat(order.getIssuedCouponId()).isNull();
        }

        @DisplayName("주문을 생성하면, orderCode(TSID)가 발급된다.")
        @Test
        void issuesOrderCode_onCreate() {
            Order order = Order.create(1L, 100_000L, 0L, null);

            assertThat(order.getOrderCode()).isNotNull();
        }

        @DisplayName("서로 다른 주문은 서로 다른 orderCode를 가진다.")
        @Test
        void issuesUniqueOrderCode() {
            Order first = Order.create(1L, 100_000L, 0L, null);
            Order second = Order.create(1L, 100_000L, 0L, null);

            assertThat(first.getOrderCode()).isNotEqualTo(second.getOrderCode());
        }

        @DisplayName("쿠폰을 적용하면, totalAmount = originalAmount - discountAmount다.")
        @Test
        void createsOrder_withCoupon() {
            Order order = Order.create(1L, 100_000L, 10_000L, 42L);

            assertThat(order.getTotalAmount()).isEqualTo(90_000L);
            assertThat(order.getDiscountAmount()).isEqualTo(10_000L);
            assertThat(order.getIssuedCouponId()).isEqualTo(42L);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(null, 100_000L, 0L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("originalAmount가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOriginalAmountIsZero() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(1L, 0L, 0L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("discountAmount가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountAmountIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> Order.create(1L, 100_000L, -1L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
