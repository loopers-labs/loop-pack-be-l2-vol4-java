package com.loopers.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderApplicationService orderApplicationService;

    public Long createOrder(String loginId, List<OrderItemRequest> items) {
        return orderApplicationService.createOrder(loginId, items);
    }
}
