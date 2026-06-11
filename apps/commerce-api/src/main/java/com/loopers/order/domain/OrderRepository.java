package com.loopers.order.domain;

import java.util.List;

public interface OrderRepository {
    Order save(Order order);
    List<Order> findByUserId(Long userId);
    List<Order> findAll();
}
