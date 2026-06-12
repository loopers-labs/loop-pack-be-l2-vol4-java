package com.loopers.application.order;

import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderCreationService orderCreationService;
    private final OrderService orderService;

    public OrderInfo placeOrder(OrderCriteria.Create criteria) {
        OrderModel order = orderCreationService.create(
            criteria.userId(),
            criteria.lines(),
            criteria.userCouponId()
        );
        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getMyOrders(Long userId) {
        List<OrderModel> orders = orderService.getOrdersByUser(userId);
        return orders.stream().map(OrderInfo::from).toList();
    }
}
