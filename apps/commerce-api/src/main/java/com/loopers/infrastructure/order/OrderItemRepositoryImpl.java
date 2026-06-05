package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public List<OrderItem> saveAll(List<OrderItem> items) {
        List<OrderItemEntity> entities = items.stream()
            .map(item -> new OrderItemEntity(
                item.getOrderId(), item.getProductId(),
                item.getProductName(), item.getProductPrice(), item.getQuantity()))
            .toList();
        return orderItemJpaRepository.saveAll(entities).stream()
            .map(OrderItemEntity::toDomain).toList();
    }

    @Override
    public List<OrderItem> findAllByOrderId(Long orderId) {
        return orderItemJpaRepository.findAllByOrderId(orderId).stream()
            .map(OrderItemEntity::toDomain).toList();
    }
}
