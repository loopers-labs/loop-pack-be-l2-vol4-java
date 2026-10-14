package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {
    CouponIssueRequest save(CouponIssueRequest request);
    Optional<CouponIssueRequest> findByRequestId(String requestId);
}
