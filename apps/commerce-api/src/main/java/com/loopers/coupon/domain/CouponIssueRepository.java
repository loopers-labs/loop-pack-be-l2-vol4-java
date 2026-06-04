package com.loopers.coupon.domain;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository {
    CouponIssueModel save(CouponIssueModel issue);
    Optional<CouponIssueModel> findById(Long id);
    Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId);
    List<CouponIssueModel> findAllByUserId(Long userId);
    List<CouponIssueModel> findAllByCouponId(Long couponId, int page, int size);
}
