package com.loopers.domain.coupon;

import java.util.List;

import org.springframework.data.domain.Page;

public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel userCoupon);

    UserCouponModel saveAndFlush(UserCouponModel userCoupon);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, int page, int size);

    UserCouponModel getActiveByIdAndUserId(Long userCouponId, Long userId);
}
