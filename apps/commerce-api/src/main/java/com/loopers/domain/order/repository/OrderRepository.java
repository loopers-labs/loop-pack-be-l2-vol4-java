package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Page<Order> findAllByMemberId(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);
}
