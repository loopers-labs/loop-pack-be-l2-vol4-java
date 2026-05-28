package com.loopers.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderApplicationService orderApplicationService;

    public Long createOrder(String loginId, List<OrderItemRequest> items) {
        return orderApplicationService.createOrder(loginId, items);
    }

    public Page<OrderSummary> getOrders(String loginId, ZonedDateTime startAt, ZonedDateTime endAt, int page, int size) {
        return orderApplicationService.getOrders(loginId, startAt, endAt, page, size);
    }

    public OrderDetail getOrder(String loginId, Long orderId) {
        return orderApplicationService.getOrder(loginId, orderId);
    }
}
