package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueResult;
import com.loopers.coupon.domain.CouponIssueStatus;

public record IssuedCouponInfo(
    UserCouponInfo coupon,
    CouponIssueStatus status
) {

    public static IssuedCouponInfo from(CouponIssueResult issueResult) {
        return new IssuedCouponInfo(
            UserCouponInfo.from(issueResult.userCoupon()),
            issueResult.status()
        );
    }
}
