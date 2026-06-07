package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponInfo issue(Long userId, Long couponPolicyId) {
        return CouponInfo.from(couponService.issue(userId, couponPolicyId), ZonedDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getMyCoupons(userId).stream()
            .map(userCoupon -> CouponInfo.from(userCoupon, now))
            .toList();
    }
}
