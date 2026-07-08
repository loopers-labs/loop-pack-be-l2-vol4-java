package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponJpaEntity, Long> {
    Optional<IssuedCouponJpaEntity> findByCouponIdAndUserLoginIdAndDeletedAtIsNull(Long couponId, String userLoginId);
}
