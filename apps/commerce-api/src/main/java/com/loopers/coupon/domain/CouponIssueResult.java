package com.loopers.coupon.domain;

public record CouponIssueResult(
    UserCoupon userCoupon,
    CouponIssueStatus status
) {

    public static CouponIssueResult issued(UserCoupon userCoupon) {
        return new CouponIssueResult(userCoupon, CouponIssueStatus.ISSUED);
    }

    public static CouponIssueResult alreadyIssued(UserCoupon userCoupon) {
        return new CouponIssueResult(userCoupon, CouponIssueStatus.ALREADY_ISSUED);
    }
}
