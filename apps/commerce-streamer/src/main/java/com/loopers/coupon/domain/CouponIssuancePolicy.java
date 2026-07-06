package com.loopers.coupon.domain;

import org.springframework.stereotype.Component;

/**
 * 발급 사전 판정(순수). 인프라 없이 테스트된다.
 * 수량 한도는 원자적 차감이 판정하므로 여기서는 존재·중복만 본다.
 */
@Component
public class CouponIssuancePolicy {

    public CouponIssueDecision decide(Coupon coupon, boolean alreadyIssued) {
        if (coupon == null) {
            return CouponIssueDecision.NOT_FOUND;
        }
        if (alreadyIssued) {
            return CouponIssueDecision.DUPLICATE;
        }
        return CouponIssueDecision.PROCEED;
    }
}
