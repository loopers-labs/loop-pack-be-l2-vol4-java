package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;

public record CouponIssueInfo(
    UserCouponInfo coupon,
    boolean newlyIssued
) {

    public static CouponIssueInfo from(CouponIssueResult result) {
        return new CouponIssueInfo(
            UserCouponInfo.from(result.userCoupon(), result.couponTemplate()),
            result.newlyIssued()
        );
    }
}
