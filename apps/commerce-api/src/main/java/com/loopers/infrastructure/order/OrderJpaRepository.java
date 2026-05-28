package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderModel, UUID> {

    @Query("SELECT o FROM OrderModel o WHERE o.userId = :userId AND o.createdAt BETWEEN :startAt AND :endAt")
    Page<OrderModel> findAllByUserIdAndCreatedAtBetween(
        @Param("userId") UUID userId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endAt") ZonedDateTime endAt,
        Pageable pageable
    );

    @Query("SELECT o FROM OrderModel o WHERE o.status = :status AND o.createdAt < :before")
    List<OrderModel> findAllByStatusAndCreatedAtBefore(
        @Param("status") OrderStatus status,
        @Param("before") ZonedDateTime before
    );
}
