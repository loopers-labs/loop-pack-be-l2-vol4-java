package com.loopers.domain.order;

import com.loopers.domain.common.Money;
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

            OrderModel order = new OrderModel(USER_ID, List.of(a, b), null, Money.ZERO);

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

            OrderModel order = new OrderModel(USER_ID, List.of(itm), null, Money.ZERO);

            assertThat(itm.getOrder()).isSameAs(order);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(null, List.of(item(1L, 100L, 1)), null, Money.ZERO));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 null이거나 비어있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenItemsAreNullOrEmpty() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, null, null, Money.ZERO)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, List.of(), null, Money.ZERO)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("금액 스냅샷은")
    @Nested
    class AmountSnapshot {

        @DisplayName("주문 전체 금액에 쿠폰 할인을 적용해 discountAmount와 finalAmount를 계산하고 적용 쿠폰을 추적한다")
        @Test
        void appliesOrderLevelDiscount() {
            // given - 항목 합계 35,000, 쿠폰(id=500)으로 5,000 할인
            OrderItem a = item(101L, 10_000L, 2);   // 20_000
            OrderItem b = item(102L, 5_000L, 3);    // 15_000

            OrderModel order = new OrderModel(USER_ID, List.of(a, b), 500L, Money.of(5_000L));

            assertAll(
                () -> assertThat(order.getTotalAmount().value()).isEqualTo(35_000L),
                () -> assertThat(order.getDiscountAmount().value()).isEqualTo(5_000L),
                () -> assertThat(order.getFinalAmount().value()).isEqualTo(30_000L),
                () -> assertThat(order.getIssuedCouponId()).isEqualTo(500L)
            );
        }

        @DisplayName("쿠폰 미적용이면 할인액은 0이고 finalAmount는 totalAmount와 같으며 적용 쿠폰은 null이다")
        @Test
        void noDiscount_whenNoCouponApplied() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(101L, 10_000L, 1)), null, Money.ZERO);

            assertAll(
                () -> assertThat(order.getDiscountAmount().value()).isZero(),
                () -> assertThat(order.getFinalAmount().value()).isEqualTo(10_000L),
                () -> assertThat(order.getIssuedCouponId()).isNull()
            );
        }

        @DisplayName("할인액이 주문 총액을 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDiscountExceedsTotal() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(USER_ID, List.of(item(101L, 10_000L, 1)), 1L, Money.of(10_001L)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰 없이 할인액이 0보다 크면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDiscountWithoutCoupon() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderModel(USER_ID, List.of(item(101L, 10_000L, 1)), null, Money.of(1_000L)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 항목 노출 시")
    @Nested
    class ItemExposure {

        @DisplayName("외부에 노출되는 items 리스트는 수정 불가능하다 (불변 컬렉션)")
        @Test
        void itemsListIsUnmodifiable() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);

            assertThrows(UnsupportedOperationException.class,
                () -> order.getItems().add(item(2L, 200L, 1)));
        }
    }

    @DisplayName("결제 확정(pay) 시")
    @Nested
    class Pay {

        @DisplayName("CREATED 주문에 pay하면 PAID로 전이된다")
        @Test
        void createdToPaid() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);

            order.pay();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("이미 PAID인 주문에 다시 pay해도 예외 없이 PAID를 유지한다 (멱등)")
        @Test
        void payIsIdempotent() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            order.pay();

            order.pay();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("이미 CANCELED인 주문에 pay하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenPayCanceled() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            order.cancel();

            CoreException ex = assertThrows(CoreException.class, order::pay);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("취소(cancel) 시")
    @Nested
    class Cancel {

        @DisplayName("CREATED 주문을 cancel하면 CANCELED로 전이된다")
        @Test
        void createdToCanceled() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @DisplayName("이미 CANCELED인 주문을 다시 cancel해도 예외 없이 CANCELED를 유지한다 (멱등)")
        @Test
        void cancelIsIdempotent() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            order.cancel();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @DisplayName("이미 PAID인 주문을 cancel하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenCancelPaid() {
            OrderModel order = new OrderModel(USER_ID, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            order.pay();

            CoreException ex = assertThrows(CoreException.class, order::cancel);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
