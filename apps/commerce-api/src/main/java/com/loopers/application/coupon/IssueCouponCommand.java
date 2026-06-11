package com.loopers.application.coupon;

public record IssueCouponCommand(
    Long userId,
    Long couponTemplateId
) {
}
