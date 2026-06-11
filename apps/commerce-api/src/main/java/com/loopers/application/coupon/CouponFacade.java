package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponListQuery userCouponListQuery;

    public IssuedCouponInfo issueCoupon(IssueCouponCommand command) {
        try {
            return IssuedCouponInfo.from(couponService.issueCoupon(command.userId(), command.couponTemplateId()));
        } catch (CoreException e) {
            return findIssuedCoupon(command).orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        return userCouponListQuery.findMyCoupons(userId, ZonedDateTime.now());
    }

    private Optional<IssuedCouponInfo> findIssuedCoupon(IssueCouponCommand command) {
        return couponService.findIssuedCoupon(command.userId(), command.couponTemplateId())
            .map(IssuedCouponInfo::from);
    }
}
