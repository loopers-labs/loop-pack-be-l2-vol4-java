package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponListQuery userCouponListQuery;

    public IssuedCouponInfo issueCoupon(IssueCouponCommand command) {
        CouponIssueResult issueResult = couponService.issueCoupon(command.userId(), command.couponTemplateId());
        return IssuedCouponInfo.from(issueResult);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        return userCouponListQuery.findMyCoupons(userId, ZonedDateTime.now());
    }
}
