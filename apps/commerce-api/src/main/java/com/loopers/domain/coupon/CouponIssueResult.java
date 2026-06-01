package com.loopers.domain.coupon;

public record CouponIssueResult(
    CouponTemplate couponTemplate,
    UserCoupon userCoupon,
    boolean newlyIssued
) {
}
