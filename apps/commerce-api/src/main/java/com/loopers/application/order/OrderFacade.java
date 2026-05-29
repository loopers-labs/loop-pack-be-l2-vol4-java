package com.loopers.application.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;

    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items) {
        var order = orderService.create(memberId, items);
        var orderItems = orderService.getItemsByOrderId(order.getId());
        return OrderInfo.of(order, orderItems);
    }

    public void cancelOrder(Long orderId, Long memberId) {
        orderService.cancel(orderId, memberId);
    }

    public OrderInfo confirmOrder(Long orderId, Long memberId) {
        var order = orderService.confirm(orderId, memberId);
        var orderItems = orderService.getItemsByOrderId(order.getId());
        return OrderInfo.of(order, orderItems);
    }

    public List<OrderInfo> getOrders(Long memberId, LocalDate startAt, LocalDate endAt) {
        return orderService.getOrders(memberId, startAt, endAt).stream()
            .map(order -> OrderInfo.of(order, orderService.getItemsByOrderId(order.getId())))
            .toList();
    }

    public OrderInfo getOrderDetail(Long orderId, Long memberId) {
        var order = orderService.getById(orderId);
        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        var orderItems = orderService.getItemsByOrderId(orderId);
        return OrderInfo.of(order, orderItems);
    }

    public List<OrderInfo> getAllOrders(LocalDate startAt, LocalDate endAt) {
        return orderService.getAllOrders(startAt, endAt).stream()
            .map(order -> OrderInfo.of(order, orderService.getItemsByOrderId(order.getId())))
            .toList();
    }

    public OrderInfo getOrderDetailForAdmin(Long orderId) {
        var order = orderService.getById(orderId);
        var orderItems = orderService.getItemsByOrderId(orderId);
        return OrderInfo.of(order, orderItems);
    }
}
