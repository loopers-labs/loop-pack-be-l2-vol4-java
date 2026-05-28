package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OrderWriter {

    private final OrderRepository orderRepository;

    public OrderResult saveOrder(OrderResult result) {
        return new OrderResult(orderRepository.save(result.order()), result.failures());
    }
}
