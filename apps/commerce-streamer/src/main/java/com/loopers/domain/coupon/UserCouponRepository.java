package com.loopers.domain.coupon;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);

    boolean existsByUserIdAndCouponPolicyId(Long userId, Long couponPolicyId);
}
