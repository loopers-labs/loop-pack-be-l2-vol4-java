package com.loopers.domain.coupon;

public record CouponIssueResult(
    CouponTemplate couponTemplate,
    UserCoupon userCoupon,
    CouponIssueStatus status
) {

    public static CouponIssueResult issued(CouponTemplate couponTemplate, UserCoupon userCoupon) {
        return new CouponIssueResult(couponTemplate, userCoupon, CouponIssueStatus.ISSUED);
    }

    public static CouponIssueResult alreadyIssued(CouponTemplate couponTemplate, UserCoupon userCoupon) {
        return new CouponIssueResult(couponTemplate, userCoupon, CouponIssueStatus.ALREADY_ISSUED);
    }
}
