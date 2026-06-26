package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderModel, UUID> {

    Optional<OrderModel> findByIdAndUserId(UUID id, UUID userId);

    // 전이 직렬화용 — 주문 행 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id")
    Optional<OrderModel> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id AND o.userId = :userId")
    Optional<OrderModel> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderModel o WHERE o.status = 'PENDING' AND o.createdAt < :before")
    List<OrderModel> findPendingBeforeForUpdate(@Param("before") ZonedDateTime before);

    @Query("SELECT o FROM OrderModel o WHERE o.status = 'PENDING' AND o.createdAt < :before")
    List<OrderModel> findPendingBefore(@Param("before") ZonedDateTime before);

    @Query("SELECT o FROM OrderModel o WHERE o.userId = :userId AND o.createdAt BETWEEN :startAt AND :endAt")
    Page<OrderModel> findAllByUserIdAndCreatedAtBetween(
        @Param("userId") UUID userId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endAt") ZonedDateTime endAt,
        Pageable pageable
    );

    Optional<OrderModel> findByIdempotencyKey(String idempotencyKey);

    /** 배치 주문 만료 처리 — @PreUpdate 우회하므로 updatedAt 직접 설정 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderModel o SET o.status = 'FAILED', o.updatedAt = :now WHERE o.id IN :ids AND o.status = 'PENDING'")
    int failAllByIds(@Param("ids") List<UUID> ids, @Param("now") ZonedDateTime now);
}
