package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findAllByUserId(Long userId);
    List<OrderEntity> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime end);
}
