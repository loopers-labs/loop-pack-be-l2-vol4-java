package com.loopers.infrastructure.order;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.order.OrderModel;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    Optional<OrderModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<OrderModel> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Page<OrderModel> findByUserIdAndDeletedAtIsNullAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(
        Long userId, ZonedDateTime startInclusive, ZonedDateTime endExclusive, Pageable pageable);

    Page<OrderModel> findByDeletedAtIsNull(Pageable pageable);
}
