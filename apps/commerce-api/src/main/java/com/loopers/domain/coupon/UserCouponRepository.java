package com.loopers.domain.coupon;

import java.util.Optional;

public interface UserCouponRepository {

    boolean issueOnce(UserCoupon userCoupon);

    Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId);
}
