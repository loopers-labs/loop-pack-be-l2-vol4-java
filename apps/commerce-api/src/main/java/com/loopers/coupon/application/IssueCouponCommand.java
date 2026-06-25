package com.loopers.coupon.application;

public record IssueCouponCommand(
    Long userId,
    Long couponTemplateId
) {
}
