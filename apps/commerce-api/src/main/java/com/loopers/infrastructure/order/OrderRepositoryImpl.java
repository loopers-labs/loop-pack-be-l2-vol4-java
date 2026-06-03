package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemEntity;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderEntity save(OrderEntity order) {
        OrderJpaEntity savedOrder = orderJpaRepository.save(OrderMapper.toJpaEntity(order));
        List<OrderItemJpaEntity> savedItems = order.getItems().stream()
                .map(item -> orderItemJpaRepository.save(OrderMapper.toItemJpaEntity(item, savedOrder.getId())))
                .toList();
        return OrderMapper.toDomain(savedOrder, savedItems);
    }

    @Override
    public Optional<OrderEntity> findById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(order -> {
                    List<OrderItemJpaEntity> items =
                            orderItemJpaRepository.findAllByOrderIdAndDeletedAtIsNull(order.getId());
                    return OrderMapper.toDomain(order, items);
                });
    }

    @Override
    public Page<OrderEntity> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdWithDateRange(userId, startAt, endAt, pageable)
                .map(order -> {
                    List<OrderItemJpaEntity> items =
                            orderItemJpaRepository.findAllByOrderIdAndDeletedAtIsNull(order.getId());
                    return OrderMapper.toDomain(order, items);
                });
    }

    @Override
    public Page<OrderEntity> findAll(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(order -> {
                    List<OrderItemJpaEntity> items =
                            orderItemJpaRepository.findAllByOrderIdAndDeletedAtIsNull(order.getId());
                    return OrderMapper.toDomain(order, items);
                });
    }
}
