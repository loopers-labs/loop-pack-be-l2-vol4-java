package com.loopers.coupon.domain;

/** 발급 사전 판정 결과. 수량 소진은 원자적 차감(tryConsumeQuota)이 판정하므로 여기 없다. */
public enum CouponIssueDecision {
    NOT_FOUND,
    DUPLICATE,
    PROCEED
}
