package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
    Long id,
    Long userId,
    Long couponId,
    CouponIssueRequestStatus status,
    String failureReason,
    Long userCouponId,
    ZonedDateTime createdAt
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(
            model.getId(),
            model.getUserId(),
            model.getCouponId(),
            model.getStatus(),
            model.getFailureReason(),
            model.getUserCouponId(),
            model.getCreatedAt()
        );
    }
}
