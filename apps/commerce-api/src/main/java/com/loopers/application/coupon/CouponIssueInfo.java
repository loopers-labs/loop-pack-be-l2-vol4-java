package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

public record CouponIssueInfo(
    Long userCouponId,
    Long userId,
    UserCouponStatus status,
    ZonedDateTime issuedAt
) {

    public static CouponIssueInfo of(UserCouponModel userCoupon, ZonedDateTime now) {
        return new CouponIssueInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getStatus(now),
            userCoupon.getCreatedAt()
        );
    }
}
