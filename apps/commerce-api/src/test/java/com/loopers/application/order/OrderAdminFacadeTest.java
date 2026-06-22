package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAdminFacadeTest {

    @InjectMocks
    private OrderAdminFacade orderAdminFacade;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("전체 주문 목록 조회 요청 시 리포지토리의 조회 로직이 호출된다.")
    void getAllOrders_ShouldCallRepository() {
        // given
        given(orderRepository.findAll()).willReturn(List.of(new OrderModel(1L)));

        // when
        List<OrderModel> result = orderAdminFacade.getAllOrders();

        // then
        assertThat(result).hasSize(1);
        verify(orderRepository).findAll();
    }

    @Test
    @DisplayName("단건 주문 상세 조회 요청 시 리포지토리의 조회 로직이 호출된다.")
    void getOrder_ShouldCallRepository() {
        // given
        Long orderId = 100L;
        given(orderRepository.findById(orderId)).willReturn(Optional.of(new OrderModel(1L)));

        // when
        orderAdminFacade.getOrder(orderId);

        // then
        verify(orderRepository).findById(orderId);
    }
}
