package com.loopers.infrastructure.order;

import com.loopers.domain.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.memberId = :memberId " +
        "AND (:startAt IS NULL OR o.createdAt >= :startAt) " +
        "AND (:endAt IS NULL OR o.createdAt <= :endAt)")
    Page<Order> findAllByMemberId(
        @Param("memberId") Long memberId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endAt") ZonedDateTime endAt,
        Pageable pageable
    );
}
