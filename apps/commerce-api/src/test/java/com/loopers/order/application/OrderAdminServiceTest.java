package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.ShippingDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderAdminServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderAdminService orderAdminService = new OrderAdminService(orderRepository);

    private Order order(Long userId, String orderNumber) {
        return Order.create(userId, orderNumber,
                ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울", "101"),
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 1)));
    }

    @Test
    @DisplayName("전체 주문 조회: 모든 사용자의 주문을 요약으로 반환한다")
    void givenOrdersOfMultipleUsers_whenGetAllOrders_thenReturnsAllSummaries() {
        when(orderRepository.findAll()).thenReturn(List.of(
                order(1L, "20260528-000001"),
                order(2L, "20260528-000002")
        ));

        List<OrderResult.Summary> result = orderAdminService.getAllOrders();

        assertThat(result)
                .hasSize(2)
                .extracting(OrderResult.Summary::userId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("전체 주문 조회: 주문이 없으면 빈 리스트를 반환한다")
    void givenNoOrders_whenGetAllOrders_thenReturnsEmpty() {
        when(orderRepository.findAll()).thenReturn(List.of());

        List<OrderResult.Summary> result = orderAdminService.getAllOrders();

        assertThat(result).isEmpty();
    }
}
