package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderReader orderReader;
    private final OrderWriter orderWriter;

    public OrderResult placeOrder(String userLoginId, List<OrderProductCommand> commands) {
        return orderWriter.placeOrder(userLoginId, commands);
    }

    public List<Order> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        return orderReader.getOrders(userLoginId, startAt, endAt, page, size);
    }

    public Order getOrder(String userLoginId, Long orderId) {
        return orderReader.getOrder(userLoginId, orderId);
    }

    public List<Order> getAllOrders(Integer page, Integer size) {
        return orderReader.getAllOrders(page, size);
    }

    public Order getOrder(Long orderId) {
        return orderReader.getOrder(orderId);
    }
}
