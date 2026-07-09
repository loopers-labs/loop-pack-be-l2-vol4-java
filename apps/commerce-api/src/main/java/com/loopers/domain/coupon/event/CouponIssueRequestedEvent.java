package com.loopers.domain.coupon.event;

public record CouponIssueRequestedEvent(Long requestId, Long userId, Long couponId) {
}
