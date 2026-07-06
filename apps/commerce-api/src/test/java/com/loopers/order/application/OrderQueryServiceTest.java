package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.domain.ShippingDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderQueryServiceTest {

    private static final Long USER_ID = 1L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderQueryService orderQueryService = new OrderQueryService(orderRepository);

    @Test
    @DisplayName("내 주문 조회: 사용자의 주문을 주문 단위 요약으로 반환한다")
    void givenUserOrders_whenGetMyOrders_thenReturnsOrderSummaries() {
        Order order = Order.create(USER_ID, "20260528-000001",
                ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울", "101"),
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 1)));
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of(order));

        List<OrderResult.Summary> result = orderQueryService.getMyOrders(USER_ID);

        assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).orderNumber()).isEqualTo("20260528-000001"),
                () -> assertThat(result.get(0).status()).isEqualTo(OrderStatus.PENDING_PAYMENT),
                () -> assertThat(result.get(0).totalAmount()).isEqualTo(29_000L)
        );
    }

    @Test
    @DisplayName("내 주문 조회: 주문이 없으면 빈 리스트를 반환한다")
    void givenNoOrders_whenGetMyOrders_thenReturnsEmpty() {
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<OrderResult.Summary> result = orderQueryService.getMyOrders(USER_ID);

        assertThat(result).isEmpty();
    }
}
