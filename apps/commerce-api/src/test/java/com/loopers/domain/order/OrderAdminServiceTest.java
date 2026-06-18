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
    @DisplayName("?꾩껜 二쇰Ц 紐⑸줉??議고쉶?섎㈃ ?쒖뒪?쒖쓽 紐⑤뱺 二쇰Ц??諛섑솚?쒕떎.")
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
    @DisplayName("二쇰Ц ID濡?二쇰Ц ?곸꽭 ?뺣낫瑜?議고쉶?섎㈃ ?대떦 二쇰Ц ?뺣낫媛 諛섑솚?쒕떎.")
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
