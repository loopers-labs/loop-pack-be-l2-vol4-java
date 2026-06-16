package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {
    OrderEntity save(OrderEntity order);
    Optional<OrderEntity> findById(Long id);
    Page<OrderEntity> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);
    Page<OrderEntity> findAll(Pageable pageable);
}
