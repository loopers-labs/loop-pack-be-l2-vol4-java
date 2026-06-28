package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {
    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> find(Long requestId);

    Optional<CouponIssueRequest> findByIdAndUserId(Long requestId, String userId);

    Optional<CouponIssueRequest> findForUpdate(Long requestId);
}
