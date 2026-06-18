package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("二쇰Ц ?앹꽦 ?붿껌 ??二쇰Ц怨??곸꽭 ?뺣낫媛 ??λ맂??")
    void createOrder_ShouldSaveOrderAndItems() {
        // given
        Long userId = 1L;
        OrderService.OrderItemRequest item = new OrderService.OrderItemRequest(
                10L, "Air Jordan", new BigDecimal("200000"), "Nike", 2
        );
        
        OrderModel order = new OrderModel(userId);
        ReflectionTestUtils.setField(order, "id", 100L);
        given(orderRepository.save(any(OrderModel.class))).willReturn(order);

        // when
        Long orderId = orderService.createOrder(userId, List.of(item));

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(orderRepository).save(any(OrderModel.class));
    }

    @Test
    @DisplayName("?좎? ID濡?二쇰Ц 紐⑸줉??議고쉶?섎㈃ ?대떦 ?좎???紐⑤뱺 二쇰Ц??諛섑솚?쒕떎.")
    void getOrders_ShouldReturnUserOrders() {
        // given
        Long userId = 1L;
        OrderModel order1 = new OrderModel(userId);
        OrderModel order2 = new OrderModel(userId);
        given(orderRepository.findAllByUserId(userId)).willReturn(List.of(order1, order2));

        // when
        List<OrderModel> result = orderService.getOrders(userId);

        // then
        assertThat(result).hasSize(2);
        verify(orderRepository).findAllByUserId(userId);
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
        OrderModel result = orderService.getOrder(orderId);

        // then
        assertThat(result.getId()).isEqualTo(orderId);
        verify(orderRepository).findById(orderId);
    }
}
