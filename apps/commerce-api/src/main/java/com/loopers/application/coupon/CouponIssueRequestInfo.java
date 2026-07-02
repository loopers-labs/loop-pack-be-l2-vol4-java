package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

public record CouponIssueRequestInfo(
    String requestId,
    Long couponId,
    CouponIssueRequestStatus status,
    String reason,
    Long userCouponId
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getRequestId(),
            request.getCouponId(),
            request.getStatus(),
            request.getReason(),
            request.getIssuedUserCouponId()
        );
    }
}
