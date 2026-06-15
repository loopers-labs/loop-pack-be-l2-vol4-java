package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> findById(Long id);
    List<UserCouponModel> findByUserId(Long userId);                       // 내 쿠폰 목록용
    List<UserCouponModel> findByCouponId(Long couponId, int page, int size); // ADMIN 발급 내역용
}
