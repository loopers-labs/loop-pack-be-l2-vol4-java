package com.loopers.tddstudy.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findById(Long id);
    Optional<UserCoupon> findByIdWithLock(Long id);
    List<UserCoupon> findAllByUserId(Long userId);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    List<UserCoupon> findAllByCouponId(Long couponId);
}
