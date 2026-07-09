package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, Long> {

    Optional<CouponIssueRequestEntity> findByUserIdAndCouponId(Long userId, Long couponId);
}
