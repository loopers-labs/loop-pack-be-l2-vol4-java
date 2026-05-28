package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderResult create(Long userId, List<OrderLine> rawLines) {
        OrderLines lines = OrderLines.of(rawLines);
        OrderModel order = OrderModel.create(userId, lines);
        OrderModel saved = orderRepository.save(order);
        List<OrderItem> items = lines.values().stream()
            .map(line -> OrderItem.of(
                saved.getId(),
                line.productId(),
                line.quantity(),
                line.productName(),
                line.productPrice(),
                line.brandName()
            ))
            .toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(items);
        return new OrderResult(saved, savedItems);
    }
}
