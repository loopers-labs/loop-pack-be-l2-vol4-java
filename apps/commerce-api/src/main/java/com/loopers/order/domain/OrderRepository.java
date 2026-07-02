package com.loopers.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> find(Long id);

    List<Order> findByMemberId(Long memberId);

    List<Order> findAll();
}
