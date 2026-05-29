package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);
    List<OrderModel> findAllByUserId(Long userId);
    List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime end);
    Page<OrderModel> findAll(Pageable pageable);
}
