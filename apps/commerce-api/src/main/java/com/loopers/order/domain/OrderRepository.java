package com.loopers.order.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);
    List<OrderModel> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
}
