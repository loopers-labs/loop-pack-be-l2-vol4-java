package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    private Product product(String name, long price, int stock) {
        return Product.create(name, "설명", Money.of(price), stock, 1L);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("쿠폰 미적용 시, 각 상품의 재고가 차감되고 원금=최종 금액인 주문이 생성된다.")
        @Test
        void createsOrder_andDecreasesStock_whenNoCoupon() {
            // arrange
            Product productA = product("상품A", 1_500L, 10);
            Product productB = product("상품B", 1_000L, 5);
            List<OrderLine> lines = List.of(
                new OrderLine(productA, 2),
                new OrderLine(productB, 1)
            );

            // act
            Order order = orderService.createOrder(1L, lines, null, null);

            // assert
            assertThat(productA.getStock()).isEqualTo(8);
            assertThat(productB.getStock()).isEqualTo(4);
            assertThat(order.getOriginalAmount()).isEqualTo(Money.of(4_000L));
            assertThat(order.getDiscountAmount()).isEqualTo(Money.zero());
            assertThat(order.getFinalAmount()).isEqualTo(Money.of(4_000L));
            assertThat(order.getUserCouponId()).isNull();
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @DisplayName("쿠폰 적용 시, 재고 차감과 함께 할인 금액이 반영된 주문이 생성된다.")
        @Test
        void createsOrder_withDiscount_whenCouponApplied() {
            // arrange
            Product productA = product("상품A", 1_500L, 10);
            List<OrderLine> lines = List.of(new OrderLine(productA, 2));

            // act
            Order order = orderService.createOrder(1L, lines, 77L, Money.of(1_000L));

            // assert
            assertThat(productA.getStock()).isEqualTo(8);
            assertThat(order.getOriginalAmount()).isEqualTo(Money.of(3_000L));
            assertThat(order.getDiscountAmount()).isEqualTo(Money.of(1_000L));
            assertThat(order.getFinalAmount()).isEqualTo(Money.of(2_000L));
            assertThat(order.getUserCouponId()).isEqualTo(77L);
        }

        @DisplayName("재고가 부족한 상품이 포함되면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenStockIsNotEnough() {
            // arrange
            Product product = product("상품A", 1_000L, 1);
            List<OrderLine> lines = List.of(new OrderLine(product, 2));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, lines, null, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLinesAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.createOrder(1L, List.of(), null, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
