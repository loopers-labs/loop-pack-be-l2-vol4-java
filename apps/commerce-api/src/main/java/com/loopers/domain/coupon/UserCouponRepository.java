package com.loopers.domain.coupon;

public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel userCoupon);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
