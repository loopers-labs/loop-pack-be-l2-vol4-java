package com.loopers.application.coupon;

public sealed interface CouponIssueRequestCriteria {

    record Enqueue(Long userId, Long couponTemplateId) implements CouponIssueRequestCriteria {}
}
