package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {
    CouponIssueRequestModel save(CouponIssueRequestModel request);
    Optional<CouponIssueRequestModel> findById(Long id);
}
