package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
           "AND (:startAt IS NULL OR o.createdAt >= :startAt) " +
           "AND (:endAt IS NULL OR o.createdAt <= :endAt)")
    List<Order> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}
