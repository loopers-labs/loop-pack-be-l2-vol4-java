package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public Order save(Order order) {
        List<OrderItem> items = order.getItems();
        Order saved = orderJpaRepository.save(order);
        items.forEach(item -> item.assignOrderId(saved.getId()));
        orderItemJpaRepository.saveAll(items);
        saved.assignItems(items);
        return saved;
    }
}
