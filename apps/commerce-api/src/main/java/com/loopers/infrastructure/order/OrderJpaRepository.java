package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("""
        SELECT o
        FROM Orders o
        WHERE o.userId = :userId
          AND (:from IS NULL OR o.createdAt >= :from)
          AND (:to IS NULL OR o.createdAt < :to)
        ORDER BY o.createdAt DESC, o.id DESC
    """)
    Page<Order> searchByUser(@Param("userId") Long userId,
                             @Param("from") ZonedDateTime from,
                             @Param("to") ZonedDateTime to,
                             Pageable pageable);
}
