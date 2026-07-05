package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

public record CouponIssueRequestInfo(Long requestId, CouponIssueRequestStatus status) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(request.getId(), request.getStatus());
    }
}
