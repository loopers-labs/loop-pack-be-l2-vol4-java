package com.loopers.order.application;

import com.loopers.common.domain.Money;
import com.loopers.coupon.application.CouponUsageService;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.domain.ShippingDestination;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCompensationServiceTest {

    private static final Long ORDER_ID = 1L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final CouponUsageService couponUsageService = mock(CouponUsageService.class);

    private final OrderCompensationService orderCompensationService = new OrderCompensationService(
            orderRepository, orderItemRepository, productStockRepository, couponUsageService
    );

    private ShippingDestination shipping() {
        return ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동");
    }

    private Order order(Long userCouponId) {
        Order order = Order.create(1L, "20260528-000001", shipping(),
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2)));
        if (userCouponId != null) {
            order.applyDiscount(userCouponId, Money.of(3_000L));
        }
        return order;
    }

    @Test
    @DisplayName("보상: 재고를 복구하고 쿠폰을 되돌리며 주문을 FAILED 로 전이한다")
    void givenOrderWithCoupon_whenCompensate_thenRestoresStockAndCouponAndFails() {
        Order order = order(50L);
        ProductStock stock = ProductStock.create(10L, 48);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2)));
        when(productStockRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(stock));

        orderCompensationService.compensate(ORDER_ID);

        assertAll(
                () -> assertThat(stock.getQuantity()).isEqualTo(50),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED)
        );
        verify(couponUsageService).restore(50L);
    }

    @Test
    @DisplayName("보상: 쿠폰 미적용 주문이면 쿠폰 복원을 호출하지 않고 재고만 복구한다")
    void givenOrderWithoutCoupon_whenCompensate_thenRestoresStockOnly() {
        Order order = order(null);
        ProductStock stock = ProductStock.create(10L, 48);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2)));
        when(productStockRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(stock));

        orderCompensationService.compensate(ORDER_ID);

        assertAll(
                () -> assertThat(stock.getQuantity()).isEqualTo(50),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED)
        );
        verify(couponUsageService, never()).restore(any());
    }
}
