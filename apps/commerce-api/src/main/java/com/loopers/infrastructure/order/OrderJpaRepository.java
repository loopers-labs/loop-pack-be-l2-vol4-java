package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderModel, UUID> {

    Optional<OrderModel> findByIdAndUserId(UUID id, UUID userId);

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

    /** 스케줄러용 — 아이템 fetch join으로 N+1 방지 */
    @Query("SELECT DISTINCT o FROM OrderModel o JOIN FETCH o.items WHERE o.status = 'PENDING' AND o.createdAt < :before")
    List<OrderModel> findAllPendingWithItemsBefore(@Param("before") ZonedDateTime before);

    /** 배치 주문 만료 처리 — @PreUpdate 우회하므로 updatedAt 직접 설정 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderModel o SET o.status = 'FAILED', o.updatedAt = :now WHERE o.id IN :ids AND o.status = 'PENDING'")
    int failAllByIds(@Param("ids") List<UUID> ids, @Param("now") ZonedDateTime now);
}
