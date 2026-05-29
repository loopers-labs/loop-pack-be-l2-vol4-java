package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
}
