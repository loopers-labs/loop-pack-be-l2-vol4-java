package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    Page<OrderModel> findAllByUserId(Long userId, Pageable pageable);
    Page<OrderModel> findAll(Pageable pageable);
}
