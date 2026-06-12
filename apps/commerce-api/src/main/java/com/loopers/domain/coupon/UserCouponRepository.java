package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> find(Long id);
    Optional<UserCouponModel> findByUserIdAndCouponId(Long userId, Long couponId);
    List<UserCouponModel> findAllByUserId(Long userId);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
