package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> findById(Long id);
    Optional<UserCouponModel> findByIdWithCoupon(Long id);
    Page<UserCouponModel> findAllByUserId(Long userId, Pageable pageable);
    Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
