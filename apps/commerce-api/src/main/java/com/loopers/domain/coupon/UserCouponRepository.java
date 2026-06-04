package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long userCouponId);

    Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId);

    boolean useAvailableCoupon(Long userCouponId, Long userId, ZonedDateTime usedAt);
}
