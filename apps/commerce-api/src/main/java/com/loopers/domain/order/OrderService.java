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

    public List<OrderModel> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        return orderReader.getOrders(userLoginId, startAt, endAt, page, size);
    }

    public OrderModel getOrder(String userLoginId, Long orderId) {
        return orderReader.getOrder(userLoginId, orderId);
    }

    public List<OrderModel> getAllOrders(Integer page, Integer size) {
        return orderReader.getAllOrders(page, size);
    }

    public OrderModel getOrder(Long orderId) {
        return orderReader.getOrder(orderId);
    }
}
