package com.loopers.domain.coupon;

import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId);
}
