package com.loopers.coupon.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findByIdForUpdate(Long id);
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
    List<UserCoupon> findByUserId(Long userId);
    Page<UserCoupon> findByCouponId(Long couponId, Pageable pageable);
}
