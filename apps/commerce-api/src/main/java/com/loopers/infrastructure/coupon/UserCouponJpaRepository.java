package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.UserCouponModel;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
