package com.loopers.infrastructure.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    Optional<OrderJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderJpaEntity> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT o FROM OrderJpaEntity o WHERE o.userId = :userId AND o.deletedAt IS NULL " +
           "AND (:startAt IS NULL OR o.createdAt >= :startAt) " +
           "AND (:endAt IS NULL OR o.createdAt <= :endAt)")
    Page<OrderJpaEntity> findAllByUserIdWithDateRange(
            @Param("userId") Long userId,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt,
            Pageable pageable);

    Page<OrderJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
