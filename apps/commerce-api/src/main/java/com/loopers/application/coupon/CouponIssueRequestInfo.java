package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueStatus;

public record CouponIssueRequestInfo(
        String requestId,
        Long couponId,
        CouponIssueStatus status,
        String reason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(model.getRequestId(), model.getCouponId(), model.getStatus(), model.getReason());
    }
}