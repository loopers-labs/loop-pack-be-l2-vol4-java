package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuedCouponJpaEntity> findWithLockById(Long id);

    List<IssuedCouponJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);

    List<IssuedCouponJpaEntity> findByCouponTemplateIdOrderByCreatedAtDesc(Long couponTemplateId, Pageable pageable);

    long countByCouponTemplateId(Long couponTemplateId);

    long countByCouponTemplateIdAndUserId(Long couponTemplateId, String userId);
}
