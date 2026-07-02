package com.loopers.domain.coupon;

public interface UserCouponRepository {
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    UserCoupon save(UserCoupon userCoupon);
}
