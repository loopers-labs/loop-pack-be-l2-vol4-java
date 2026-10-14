package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final UserCouponService userCouponService;

    public CouponInfo.MyCoupon issue(Long userId, Long couponTemplateId) {
        UserCoupon issued = userCouponService.issue(userId, couponTemplateId);
        return CouponInfo.MyCoupon.from(issued);
    }

    public List<CouponInfo.MyCoupon> getMyCoupons(Long userId) {
        return userCouponService.getMyCoupons(userId).stream()
            .map(CouponInfo.MyCoupon::from)
            .toList();
    }
}
