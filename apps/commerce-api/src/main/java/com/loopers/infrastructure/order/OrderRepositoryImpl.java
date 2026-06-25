package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public OrderEntity save(OrderEntity order) {
        return OrderMapper.toDomain(orderJpaRepository.save(OrderMapper.toJpaEntity(order)));
    }

    @Override
    public Optional<OrderEntity> findById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(OrderMapper::toDomain);
    }

    @Override
    public Optional<OrderEntity> findByIdWithLock(Long id) {
        return orderJpaRepository.findByIdWithLock(id).map(OrderMapper::toDomain);
    }

    @Override
    public Page<OrderEntity> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdWithDateRange(userId, startAt, endAt, pageable)
                .map(OrderMapper::toDomain);
    }

    @Override
    public Page<OrderEntity> findAll(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(OrderMapper::toDomain);
    }
}
