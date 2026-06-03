package com.loopers.infrastructure.ordering.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderJpaEntity> findWithLockById(Long id);

    Optional<OrderJpaEntity> findByIdAndUserId(Long id, String userId);

    List<OrderJpaEntity> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        String userId,
        ZonedDateTime startAt,
        ZonedDateTime endAt
    );

    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
