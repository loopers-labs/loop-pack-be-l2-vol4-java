package com.loopers.application.order;

import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands) {
        OrderResult result = orderService.placeOrder(userLoginId, commands);
        return OrderInfo.from(result);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        return orderService.getOrders(userLoginId, startAt, endAt, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String userLoginId, Long orderId) {
        return OrderInfo.from(orderService.getOrder(userLoginId, orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(Integer page, Integer size) {
        return orderService.getAllOrders(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }
}
