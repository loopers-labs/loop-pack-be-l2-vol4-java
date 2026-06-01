package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {
    Optional<OrderModel> findByIdAndDeletedAtIsNull(Long id);
    List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
}
