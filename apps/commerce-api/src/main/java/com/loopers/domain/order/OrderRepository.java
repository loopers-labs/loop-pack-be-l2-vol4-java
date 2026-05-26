package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> find(Long id);

    List<Order> findByUserId(Long userId);
}
