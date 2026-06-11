package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponIssue issueCoupon(Long userId, Long couponTemplateId) {
        return couponService.issue(userId, couponTemplateId);
    }
}
