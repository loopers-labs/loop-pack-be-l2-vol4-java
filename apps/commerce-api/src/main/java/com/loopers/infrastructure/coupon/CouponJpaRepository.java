package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.CouponModel;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {
}
