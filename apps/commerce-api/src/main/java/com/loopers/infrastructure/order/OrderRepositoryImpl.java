package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public OrderItemModel saveItem(OrderItemModel item) {
        return orderItemJpaRepository.save(item);
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<OrderItemModel> findItemsByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderId(orderId);
    }
}
