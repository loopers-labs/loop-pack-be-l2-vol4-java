package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return orderItemJpaRepository.save(orderItem);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderId(orderId);
    }
}
