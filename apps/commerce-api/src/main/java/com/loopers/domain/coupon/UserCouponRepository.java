package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> findById(Long id);
    List<UserCouponModel> findAllByUserId(Long userId);
    Page<UserCouponIssue> findAllByCouponId(Long couponId, Pageable pageable);
    boolean useIfIssued(Long id, ZonedDateTime usedAt);
}
