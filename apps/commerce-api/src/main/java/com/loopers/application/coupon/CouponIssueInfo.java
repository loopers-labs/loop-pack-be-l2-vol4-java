package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

public record CouponIssueInfo(
        Long userCouponId,
        Long userId,
        Long couponId,
        boolean used,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt
) {
    public static CouponIssueInfo from(UserCouponModel userCoupon) {
        return new CouponIssueInfo(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.isUsed(),
                userCoupon.getUsedAt(),
                userCoupon.getCreatedAt()
        );
    }
}