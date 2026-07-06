package com.loopers.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PlaceOrderFacade {

    private final PlaceOrderService placeOrderService;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderResult.Detail place(OrderCommand.Create command) {
        String orderNumber = orderNumberGenerator.generate();
        return placeOrderService.createPendingOrder(command, orderNumber);
    }
}
