package com.loopers.domain.coupon;

import java.util.List;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    List<UserCoupon> findByUserId(Long userId);
}
