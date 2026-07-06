package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findById(Long id);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId); // 중복 발급 방지 확인용
    List<UserCoupon> findByUserId(Long userId);
    List<UserCoupon> findByCouponId(Long couponId, int page, int size);
    long countByCouponId(Long couponId);
}
