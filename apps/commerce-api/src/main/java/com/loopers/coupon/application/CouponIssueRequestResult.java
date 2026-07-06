package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueRequestStatus;

public record CouponIssueRequestResult(
        String requestId,
        CouponIssueRequestStatus status,
        String reason
) {
}
