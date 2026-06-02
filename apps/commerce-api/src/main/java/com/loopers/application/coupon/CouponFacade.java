package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.DuplicateCouponIssueException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    public IssuedCouponInfo issueCoupon(IssueCouponCommand command) {
        try {
            CouponIssueResult issueResult = couponService.issueCoupon(command.userId(), command.couponTemplateId());
            return IssuedCouponInfo.from(issueResult);
        } catch (DuplicateCouponIssueException e) {
            CouponIssueResult issueResult = couponService.getAlreadyIssuedCoupon(
                command.userId(),
                command.couponTemplateId()
            );
            return IssuedCouponInfo.from(issueResult);
        }
    }
}
