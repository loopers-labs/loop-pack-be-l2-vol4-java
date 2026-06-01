package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    public CouponIssueInfo issueCoupon(IssueCouponCommand command) {
        CouponIssueResult result = couponService.issueCoupon(command.userId(), command.couponTemplateId());
        return CouponIssueInfo.from(result);
    }
}
