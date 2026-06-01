package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueStatus;

public record CouponIssueInfo(
    UserCouponInfo coupon,
    CouponIssueStatus status
) {

    public static CouponIssueInfo from(CouponIssueResult result) {
        return new CouponIssueInfo(
            UserCouponInfo.from(result.userCoupon(), result.couponTemplate()),
            result.status()
        );
    }
}
