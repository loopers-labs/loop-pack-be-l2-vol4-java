package com.loopers.domain.order;

import com.loopers.domain.coupon.CouponSnapshot;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private static final Long USER_ID = 1L;

    private final OrderService orderService = new OrderService(); // 순수 POJO — 레포 의존 없음

    private ProductModel product(String name, long price, int stock) {
        return new ProductModel(1L, name, "설명", price, stock);
    }

    @DisplayName("주문을 조립할 때, ")
    @Nested
    class Place {

        @DisplayName("재고를 차감하고 PENDING 주문을 조립한다.")
        @Test
        void decreasesStockAndAssembles() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 10);

            // act
            OrderModel order = orderService.place(USER_ID, List.of(new OrderLine(product, 2)));

            // assert
            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(8),
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getTotalAmount()).isEqualTo(2000L),
                () -> assertThat(order.getItems()).hasSize(1)
            );
        }

        @DisplayName("여러 상품이면 각 재고를 차감하고 총액을 합산한다.")
        @Test
        void handlesMultipleLines() {
            // arrange
            ProductModel p1 = product("상품1", 1000L, 10);
            ProductModel p2 = product("상품2", 500L, 10);

            // act
            OrderModel order = orderService.place(USER_ID, List.of(new OrderLine(p1, 2), new OrderLine(p2, 3)));

            // assert
            assertAll(
                () -> assertThat(p1.getStock()).isEqualTo(8),
                () -> assertThat(p2.getStock()).isEqualTo(7),
                () -> assertThat(order.getTotalAmount()).isEqualTo(3500L),
                () -> assertThat(order.getItems()).hasSize(2)
            );
        }

        @DisplayName("스냅샷은 주문 시점의 상품명·단가·수량을 보존한다.")
        @Test
        void preservesSnapshot() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 10);

            // act
            OrderModel order = orderService.place(USER_ID, List.of(new OrderLine(product, 2)));

            // assert
            OrderItemModel item = order.getItems().get(0);
            assertAll(
                () -> assertThat(item.getProductNameSnapshot()).isEqualTo("에어맥스"),
                () -> assertThat(item.getPriceSnapshot()).isEqualTo(1000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockInsufficient() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 1);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.place(USER_ID, List.of(new OrderLine(product, 5))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityNegative() {
            // arrange
            ProductModel product = product("에어맥스", 1000L, 10);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.place(USER_ID, List.of(new OrderLine(product, -1))));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmpty() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> orderService.place(USER_ID, List.of()));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰과 함께 주문을 조립할 때, ")
    @Nested
    class PlaceWithCoupon {

        private static final ZonedDateTime NOW = ZonedDateTime.now();

        private UserCoupon fixedCoupon(long discountValue) {
            CouponSnapshot snapshot = new CouponSnapshot("정액 할인", CouponType.FIXED, discountValue, null);
            return new UserCoupon(USER_ID, 10L, snapshot, NOW, NOW.plusDays(30));
        }

        @DisplayName("쿠폰을 사용 처리하고 할인이 반영된 주문을 조립한다.")
        @Test
        void usesCouponAndAppliesDiscount() {
            // arrange
            ProductModel product = product("에어맥스", 10000L, 10);
            UserCoupon userCoupon = fixedCoupon(5000L);

            // act
            OrderModel order = orderService.place(USER_ID, List.of(new OrderLine(product, 2)), userCoupon, NOW);

            // assert
            assertAll(
                () -> assertThat(order.getTotalAmount()).isEqualTo(20000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(5000L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(15000L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(product.getStock()).isEqualTo(8)
            );
        }

        @DisplayName("이미 사용된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_whenCouponAlreadyUsed() {
            // arrange
            ProductModel product = product("에어맥스", 10000L, 10);
            UserCoupon userCoupon = fixedCoupon(5000L);
            userCoupon.use(USER_ID, NOW);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.place(USER_ID, List.of(new OrderLine(product, 2)), userCoupon, NOW));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("타 유저의 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_whenNotOwner() {
            // arrange
            ProductModel product = product("에어맥스", 10000L, 10);
            CouponSnapshot snapshot = new CouponSnapshot("정액 할인", CouponType.FIXED, 5000L, null);
            UserCoupon othersCoupon = new UserCoupon(999L, 10L, snapshot, NOW, NOW.plusDays(30));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.place(USER_ID, List.of(new OrderLine(product, 2)), othersCoupon, NOW));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("쿠폰이 null 이면 할인 없이 조립한다.")
        @Test
        void assemblesWithoutDiscount_whenCouponNull() {
            // arrange
            ProductModel product = product("에어맥스", 10000L, 10);

            // act
            OrderModel order = orderService.place(USER_ID, List.of(new OrderLine(product, 1)), null, NOW);

            // assert
            assertAll(
                () -> assertThat(order.getDiscountAmount()).isEqualTo(0L),
                () -> assertThat(order.getFinalAmount()).isEqualTo(10000L)
            );
        }
    }
}
