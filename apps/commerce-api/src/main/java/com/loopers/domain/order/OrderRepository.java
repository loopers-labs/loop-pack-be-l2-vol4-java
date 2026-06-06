package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);

    List<OrderModel> findByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime fromInclusive, ZonedDateTime toExclusive);

    Page<OrderModel> findAll(Pageable pageable);
}
