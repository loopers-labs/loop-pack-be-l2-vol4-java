package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> findById(UUID id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<OrderModel> findByIdAndUserId(UUID id, UUID userId) {
        return orderJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<OrderModel> findByIdForUpdate(UUID id) {
        return orderJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<OrderModel> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
        return orderJpaRepository.findByIdAndUserIdForUpdate(id, userId);
    }

    @Override
    public List<OrderModel> findPendingBeforeForUpdate(ZonedDateTime before) {
        return orderJpaRepository.findPendingBeforeForUpdate(before);
    }

    @Override
    public Page<OrderModel> findAllByUserId(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt, pageable);
    }

    @Override
    public Page<OrderModel> findAll(Pageable pageable) {
        return orderJpaRepository.findAll(pageable);
    }

    @Override
    public Optional<OrderModel> findByIdempotencyKey(String idempotencyKey) {
        return orderJpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public int failAllByIds(List<UUID> orderIds, ZonedDateTime now) {
        return orderJpaRepository.failAllByIds(orderIds, now);
    }
}
