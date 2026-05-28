package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderRepository {
    OrderEntity save(OrderEntity order);
    Optional<OrderEntity> findById(Long id);
    Page<OrderEntity> findAllByUserId(Long userId, Pageable pageable);
}
