package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
        String requestId,
        Long couponTemplateId,
        Long userId,
        CouponIssueRequestStatus status,
        Long issuedCouponId,
        String failureReason,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(
                model.getRequestId(),
                model.getCouponTemplateId(),
                model.getUserId(),
                model.getStatus(),
                model.getIssuedCouponId(),
                model.getFailureReason(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
