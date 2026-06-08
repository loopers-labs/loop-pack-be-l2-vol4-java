package com.loopers.application.order;

import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderService orderService;
    private final ProductStockService productStockService;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemInput> items) {
        List<OrderLine> lines = OrderLine.from(items);
        List<ProductStockModel> stocks = lines.stream()
                .map(line -> productStockService.decrease(line.stockId(), line.quantity()))
                .toList();
        return OrderInfo.from(orderService.placeOrder(new OrderModel(userId), stocks, lines));
    }

    @Transactional
    public OrderInfo cancelOrder(Long orderId, Long userId) {
        OrderModel order = orderService.cancel(orderId, userId);
        order.getItems().forEach(item -> productStockService.increase(item.getStockId(), item.getQuantity().getValue()));
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAdminOrders(Pageable pageable) {
        return orderService.getAdminList(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrder(Long orderId) {
        return OrderInfo.from(orderService.get(orderId));
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Long userId) {
        return OrderInfo.from(orderService.getByUser(orderId, userId));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime start = startAt != null ? startAt.atStartOfDay(KST) : null;
        ZonedDateTime end = endAt != null ? endAt.atTime(LocalTime.MAX).atZone(KST) : null;
        return orderService.getList(userId, start, end).stream()
                .map(OrderInfo::from)
                .toList();
    }

}
