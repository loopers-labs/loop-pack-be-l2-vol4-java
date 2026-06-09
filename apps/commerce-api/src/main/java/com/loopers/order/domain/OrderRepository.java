package com.loopers.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);

    Optional<OrderModel> find(Long id);

    List<OrderModel> findByMemberId(Long memberId);

    List<OrderModel> findAll();
}
