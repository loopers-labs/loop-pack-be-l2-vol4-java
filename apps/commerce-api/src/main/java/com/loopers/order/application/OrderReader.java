package com.loopers.order.application;

import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderReader {

    private final OrderRepository orderRepository;

    public Optional<OrderInfo> findForPayment(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(order -> new OrderInfo(order.getOrderNumber(), order.isPayable(), order.getFinalAmount().value()));
    }
}
