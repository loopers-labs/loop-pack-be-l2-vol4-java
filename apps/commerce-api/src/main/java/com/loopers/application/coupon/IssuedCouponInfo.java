package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueStatus;

public record IssuedCouponInfo(
    UserCouponInfo coupon,
    CouponIssueStatus status
) {

    public static IssuedCouponInfo from(CouponIssueResult issueResult) {
        return new IssuedCouponInfo(
            UserCouponInfo.from(issueResult.userCoupon(), issueResult.couponTemplate()),
            issueResult.status()
        );
    }
}
