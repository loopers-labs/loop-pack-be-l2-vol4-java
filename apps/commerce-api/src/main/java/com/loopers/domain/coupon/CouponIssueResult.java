package com.loopers.domain.coupon;

public record CouponIssueResult(
    UserCoupon userCoupon,
    CouponIssueStatus status
) {

    public static CouponIssueResult issued(UserCoupon userCoupon) {
        return new CouponIssueResult(userCoupon, CouponIssueStatus.ISSUED);
    }
}
