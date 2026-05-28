package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.Order;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
}
