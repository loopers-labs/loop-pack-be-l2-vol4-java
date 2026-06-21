package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CouponTemplateJpaEntity> findWithLockByIdAndDeletedAtIsNull(Long id);

    List<CouponTemplateJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
