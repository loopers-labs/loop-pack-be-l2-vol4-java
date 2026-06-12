package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    Optional<OrderModel> findByIdAndUserId(Long id, Long userId);
    Optional<OrderModel> findByOrderNumber(String orderNumber);
    List<OrderModel> findAllByUserId(Long userId);
    List<OrderModel> findAllByUserIdWithDateRange(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
    Page<OrderModel> findAll(Pageable pageable);
    boolean cancelIfRequested(Long id, Long userId);
}
