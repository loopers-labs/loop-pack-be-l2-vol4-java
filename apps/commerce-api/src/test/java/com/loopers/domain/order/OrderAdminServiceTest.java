package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAdminServiceTest {

    @InjectMocks
    private OrderAdminService orderAdminService;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("전체 주문 목록을 조회하면 시스템의 모든 주문이 반환된다.")
    void getAllOrders_ShouldReturnAllOrders() {
        // given
        OrderModel order1 = new OrderModel(1L);
        OrderModel order2 = new OrderModel(2L);
        given(orderRepository.findAll()).willReturn(List.of(order1, order2));

        // when
        List<OrderModel> result = orderAdminService.getAllOrders();

        // then
        assertThat(result).hasSize(2);
        verify(orderRepository).findAll();
    }

    @Test
    @DisplayName("주문 ID로 주문 상세 정보를 조회하면 해당 주문 정보가 반환된다.")
    void getOrder_ShouldReturnOrderDetail() {
        // given
        Long orderId = 100L;
        OrderModel order = new OrderModel(1L);
        ReflectionTestUtils.setField(order, "id", orderId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        OrderModel result = orderAdminService.getOrder(orderId);

        // then
        assertThat(result.getId()).isEqualTo(orderId);
        verify(orderRepository).findById(orderId);
    }
}
