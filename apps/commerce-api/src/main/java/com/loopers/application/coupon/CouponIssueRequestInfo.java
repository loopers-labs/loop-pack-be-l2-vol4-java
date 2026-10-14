package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
    String requestId,
    CouponIssueStatus status,
    Long userCouponId,
    String rejectReason,
    ZonedDateTime resolvedAt
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getRequestId(),
            request.getStatus(),
            request.getUserCouponId(),
            request.getRejectReason(),
            request.getResolvedAt()
        );
    }
}
