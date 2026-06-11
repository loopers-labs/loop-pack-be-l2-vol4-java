package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> find(Long id);
    List<UserCoupon> findAllByUserId(Long userId);
    List<UserCoupon> findAllByCouponId(Long couponId, int page, int size);
}
