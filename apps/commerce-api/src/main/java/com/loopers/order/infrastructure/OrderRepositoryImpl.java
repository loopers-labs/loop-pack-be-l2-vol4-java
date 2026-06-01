package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<OrderModel> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt);
    }
}
