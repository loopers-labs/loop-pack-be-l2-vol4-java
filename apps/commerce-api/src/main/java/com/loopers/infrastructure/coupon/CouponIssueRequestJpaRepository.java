package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestJpaEntity, Long> {
    Optional<CouponIssueRequestJpaEntity> findByIdAndUserId(Long id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CouponIssueRequestJpaEntity> findWithLockById(Long id);
}
