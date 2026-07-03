package com.loopers.coupon.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssuancePolicyTest {

    private final CouponIssuancePolicy policy = new CouponIssuancePolicy();

    private Coupon coupon() {
        return Coupon.createLimited("선착순", CouponType.FIXED, 1_000L, null, ZonedDateTime.now().plusDays(1), 10L);
    }

    @Test
    @DisplayName("쿠폰이 없으면 NOT_FOUND")
    void givenNoCoupon_thenNotFound() {
        assertThat(policy.decide(null, false)).isEqualTo(CouponIssueDecision.NOT_FOUND);
    }

    @Test
    @DisplayName("이미 발급받은 사용자면 DUPLICATE")
    void givenAlreadyIssued_thenDuplicate() {
        assertThat(policy.decide(coupon(), true)).isEqualTo(CouponIssueDecision.DUPLICATE);
    }

    @Test
    @DisplayName("존재하고 미발급이면 PROCEED")
    void givenIssuable_thenProceed() {
        assertThat(policy.decide(coupon(), false)).isEqualTo(CouponIssueDecision.PROCEED);
    }
}
