package com.loopers.interfaces.event.coupon;

public record CouponIssueRequestEvent(Long memberId, Long templateId) {
}
