package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;

    public OrderInfo createOrder(Long userId, List<OrderService.OrderItemCommand> commands) {
        return OrderInfo.from(orderService.createOrder(userId, commands));
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getOrders(Long userId, String startAt, String endAt) {
        return orderService.getOrders(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }
}
