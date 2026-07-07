package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;

public record CouponIssueResultInfo(
    String requestId, Long couponId, String status, Long userCouponId, String reason) {
    public static CouponIssueResultInfo from(CouponIssueResult r) {
        return new CouponIssueResultInfo(
            r.getRequestId(), r.getCouponId(), r.getStatus().name(), r.getUserCouponId(), r.getReason());
    }
}
